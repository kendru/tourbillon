(ns tourbillon.www.core
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :as server]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.util.request :refer [body-string]]
            [ring.util.response :refer [response not-found content-type resource-response]]
            [ring.middleware.params :refer :all]
            [ring.middleware.json :refer :all]
            [slingshot.slingshot :refer [try+]]
            [buddy.auth :refer [throw-unauthorized]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [buddy.auth.accessrules :refer [wrap-access-rules]]
            [tourbillon.workflow.jobs :refer [create-job create-transition emit!]]
            [tourbillon.event.core :refer [create-event]]
            [tourbillon.schedule.core :refer [send-event!]]
            [tourbillon.storage.object :refer :all]
            [tourbillon.auth.accounts :as accounts]
            [tourbillon.auth.core :as auth]
            [tourbillon.template.core :as template]
            [taoensso.timbre :as log]))

(def access-rules [{:uris ["/" "/api/api-keys" "/api/session-tokens"]
                    :handler auth/any-access}
                   {:pattern #"^/assets/.*"
                    :handler auth/any-access}
                   {:uri "/api/events"
                    :request-method :post
                    :handler (auth/restrict-to "create-events")}
                   {:uri "/api/workflows"
                    :request-method :post
                    :handler (auth/restrict-to "create-workflows")}
                   {:uri "/api/workflows/:id"
                    :request-method :get
                    :handler (auth/restrict-to "get-workflows")}
                   {:uri "/api/jobs"
                    :request-method :post
                    :handler (auth/restrict-to "create-jobs")}
                   {:uri "/api/jobs/:id"
                    :request-method :get
                    :handler (auth/restrict-to "get-jobs")}
                   {:uri "/api/jobs/:id"
                    :request-method :post
                    :handler (auth/restrict-to "create-events")}
                   {:uri "/api/templates"
                    :request-method :post
                    :handler (auth/restrict-to "create-templates")}
                  {:uri "/api/templates/:id"
                    :request-method :get
                    :handler (auth/restrict-to "get-templates")}])

(defn- json-request? [req]
  (= "application/json" (get-in req [:headers "content-type"])))

(defn app-routes [job-store account-store template-store scheduler]
  (routes
    (GET "/" [] (content-type
                  (resource-response "index.html" {:root "public"})
                  "text/html"))

    (route/resources "/assets")

    (context "/api" {{:keys [api-key]} :identity}
      (POST "/api-keys" []
            (response (accounts/create-api-key! account-store)))
      (POST "/session-tokens" {{:keys [api-key api-secret]} :body}
            (response (accounts/create-session-token account-store api-key api-secret)))

      ; (context "/workflows" []
      ;   (POST "/" {data :body}
      ;     ()))

      (context "/events" []
        (POST "/" {{:keys [at every subscriber data]
                    :or {every nil
                         data {}}} :body}
              (let [self-transition (create-transition "start" "start" "trigger" [subscriber])
                job (create! job-store (create-job nil api-key [self-transition] "start"))
                event (create-event "trigger" (:id job) at every data)]
            (send-event! scheduler event)
            (response event))))
      (context "/jobs" []
        (POST "/" {{:keys [transitions current-state]
                    :or {transitions []
                         current-state "start"}
                    :as data} :body}
          (let [job (create-job nil api-key transitions current-state)]
            (response
              (create! job-store job))))
        
        (context "/:id" [id]
          (GET "/" []
            (if-let [job (find-by-id job-store id)]
              (if (= (:api-key job) api-key)
                (response job)
                (throw-unauthorized "Job does not match api key"))

              (not-found {:status "error" :msg "No such job"})))
        
          (POST "/" {{:keys [event data]} :body}
            (if-let [job (find-by-id job-store id)]
              (response
                (emit! job-store (create-event event id data)))
              (not-found {:status "error" :msg "No such job"})))))

      (context "/templates" []
        (POST "/" {:as req}
          (let [text (if (json-request? req)
                       (get-in req [:body :text])
                       (body-string req))]
            (println "Template text:" text)
            (try+
              (response {:id (template/create-template! template-store api-key text)})
              (catch Object _
                template/malformed-template-response))))))

    (not-found {:status "error" :msg "Not found"})))

(defrecord Webserver [ip port connection job-store account-store template-store scheduler]
  component/Lifecycle

  (start [component]
    (log/info "Starting web server")

    (let [app (-> (app-routes job-store account-store template-store scheduler)
                  handler/api
                  (wrap-access-rules {:rules access-rules :on-error auth/on-error})
                  (wrap-authorization auth/backend)
                  (wrap-authentication auth/backend)
                  (wrap-json-body {:keywords? true})
                  (wrap-json-response {:pretty true}))
          conn (server/run-server app {:ip ip :port port})]
      (assoc component :connection conn)))

  (stop [component]
    (log/info "Stopping web server")

        (connection)
        (dissoc component :connection)))

(defn new-server [ip port]
  (map->Webserver {:ip ip :port port}))
