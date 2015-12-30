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
            [clj-time.core :as t]
            [clj-time.coerce :as time-coerce]
            [tourbillon.workflow.jobs :refer [get-job-status add-transition remove-transition update-subscribers emit! Workflow->Job]]
            [tourbillon.event.core :refer [create-event]]
            [tourbillon.event.cron :refer [parse-cron get-next-time]]
            [tourbillon.schedule.core :refer [send-event!]]
            [tourbillon.storage.object :refer :all]
            [tourbillon.auth.accounts :as accounts]
            [tourbillon.auth.core :as auth]
            [tourbillon.template.core :as template]
            [tourbillon.www.utils :refer :all]
            [tourbillon.www.middleware :refer :all]
            [tourbillon.utils :as utils]
            [taoensso.timbre :as log]))

(defn- get-at
  "Get the initial time at which to fire an event, given
  an initial time and cron spec that may be nil"
  [at cron]
  (if cron
    (/ (time-coerce/to-long (get-next-time (t/now) cron)) 1000)
    (if at
      (max at (+ 1 (utils/get-time))))))

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

(defn workflow-routes [api-key workflow-store]
  (routes
   (POST "/" {{:keys [transitions start-state]
               :or {transitions []}
               :as data} :body}
         (response
          (create! workflow-store {:api-key api-key
                                   :start-state start-state
                                   :transitions transitions})))
   (GET "/:id" [id]
        (if-let [workflow (find-by-id workflow-store id)]
          (if (= (:api-key workflow) api-key)
            (response workflow)
            (throw-unauthorized "Workflow does not match api key"))
          (not-found {:status "error" :msg "No such workflow"})))))

(defn event-routes [api-key job-store scheduler]
  (routes
   (POST "/" {{:keys [at every cron subscriber data]
               :or {data {}}} :body}
         (let [self-transition {:from "start" :to "start" :on "trigger" :subscribers [subscriber]}
               at (get-at at (when cron (parse-cron cron)))
               job (create! job-store {:api-key api-key
                                       :current-state "start"
                                       :transitions [self-transition]})
               event (create-event "trigger" (:id job) at (or cron every) data)]
           (send-event! scheduler event)
           (response event)))))

(defn job-routes [api-key job-store workflow-store subscriber-system]
  (routes
   (POST "/" {{:keys [transitions current-state workflow-id]
               :or {transitions []
                    current-state "start"}
               :as data} :body}
         (if workflow-id
           (if-let [workflow (find-by-id workflow-store workflow-id)]
             (if (= (:api-key workflow) api-key)
               (let [job (Workflow->Job workflow)]
                 (response
                  (create! job-store (if (seq transitions)
                                       (reduce update-subscribers job transitions)
                                        job))))
               (throw-unauthorized "Workflow does not match api key"))
             (not-found {:status "error" :msg "No such workflow"}))
           (response
            (create! job-store {:api-key api-key
                                :current-state current-state
                                :transitions transitions}))))
   (context "/:id" [id]
            (GET "/" []
                 (if-let [job (find-by-id job-store id)]
                   (if (= (:api-key job) api-key)
                     (response job)
                     (throw-unauthorized "Job does not match api key"))
                   
                   (not-found {:status "error" :msg "No such job"})))
            
            (GET "/current-state" []
                 (if-let [job (find-by-id job-store id)]
                   (if (= (:api-key job) api-key)
                     (response (get-job-status job))
                     (throw-unauthorized "Job does not match api key"))
                   (not-found {:status "error" :msg "No such job"})))
            
            (POST "/" {{:keys [event data]} :body}
                  (if-let [job (find-by-id job-store id)]
                    (if (= (:api-key job) api-key)
                      (response
                       (emit! job-store subscriber-system (create-event event id data)))
                      (throw-unauthorized "Job does not match api key"))
                    (not-found {:status "error" :msg "No such job"})))
            
            ;; Creates a new transition and returns the updated job
            (POST "/transitions" {transition :body}
                  (if-let [job (find-by-id job-store id)]
                    (if (= (:api-key job) api-key)
                      (response
                       (update! job-store job #(add-transition % transition)))
                      (throw-unauthorized "Job does not match api key"))
                    (not-found {:status "error" :msg "No such job"})))
            
            (PUT "/transitions" {transition :body}
                 (if-let [job (find-by-id job-store id)]
                   (if (= (:api-key job) api-key)
                     (response
                      (update! job-store job #(update-subscribers % transition)))
                     (throw-unauthorized "Job does not match api key"))
                   (not-found {:status "error" :msg "No such job"})))
            
            ;; Removes transition from job
            (DELETE "/transitions" {transition :body}
                    (if-let [job (find-by-id job-store id)]
                      (if (= (:api-key job) api-key)
                        (response
                         (update! job-store job #(remove-transition % transition)))
                        (throw-unauthorized "Job does not match api key"))
                      (not-found {:status "error" :msg "No such job"}))))))

(defn template-routes [api-key template-store]
  (routes
   (POST "/" {:as req}
         (let [text (if (json-request? req)
                      (get-in req [:body :text])
                      (body-string req))]
           (try+
            (response {:id (template/create-template! template-store api-key text)})
            (catch Object _
              template/malformed-template-response))))))

(defn app-routes [job-store workflow-store account-store template-store scheduler subscriber-system]
  (routes
    (GET "/" [] (content-type
                  (resource-response "index.html" {:root "public"})
                  "text/html"))

    (route/resources "/assets")

    (context "/api" {{:keys [api-key] :as ident} :identity}
      (POST "/api-keys" []
            (response (accounts/create-api-key! account-store)))
      (POST "/session-tokens" {{:keys [api-key api-secret]} :body}
            (response (accounts/create-session-token account-store api-key api-secret)))

      (context "/workflows" []
               (-> (workflow-routes api-key workflow-store)
                   (wrap-routes without-api-key)))

      (context "/events" []
               (event-routes api-key job-store scheduler))

      (context "/jobs" []
               (-> (job-routes api-key job-store workflow-store subscriber-system)
                   (wrap-routes without-api-key)))

      (context "/templates" []
               (template-routes api-key template-store)))

    (not-found {:status "error" :msg "Not found"})))

(defrecord Webserver [ip port connection job-store workflow-store account-store template-store scheduler subscriber-system]
  component/Lifecycle

  (start [component]
    (log/info "Starting web server")

    (let [app (-> (app-routes job-store workflow-store account-store template-store scheduler subscriber-system)
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

(defn new-server [{:keys [ip port]}]
  (map->Webserver {:ip ip :port port}))
