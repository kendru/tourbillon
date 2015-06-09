(ns tourbillon.www.core
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :as server]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.util.response :refer [response content-type resource-response]]
            [ring.middleware.params :refer :all]
            [ring.middleware.json :refer :all]
            [tourbillon.workflow.jobs :refer [create-job create-transition emit!]]
            [tourbillon.event.core :refer [create-event]]
            [tourbillon.schedule.core :refer [send-event!]]
            [tourbillon.storage.object :refer :all]
            [taoensso.timbre :as log]))

(defn app-routes [job-store scheduler]
  (routes
    (GET "/" [] (content-type
                  (resource-response "index.html" {:root "public"})
                  "text/html"))

    (route/resources "/assets")

    (context "/api" []
      ; (context "/workflows" []
      ;   (POST "/" {data :body}
      ;     ()))
      (context "/events" []
        (POST "/" {{:keys [at every subscriber data]
                    :or {every nil
                         data {}}} :body}
          (let [job (create! job-store (create-job nil
                                                   [(create-transition "start" "start" "trigger" [subscriber])]
                                                   "start"))
                event (create-event "trigger" (:id job) at every data)]
            (send-event! scheduler event)
            (response event))))
      (context "/jobs" []
        (POST "/" {{:keys [transitions current-state]
                    :or {transitions []
                         current-state "start"}
                    :as data} :body}
          (let [job (create-job nil transitions current-state)]
            (response
              (create! job-store job))))
        
        (context "/:id" [id]
          (GET "/" []
            (if-let [job (find-by-id job-store (read-string id))]
              (response job)
              (response "No such job")))
        
          (POST "/" {{:keys [event data]} :body}
            (if-let [job (find-by-id job-store (read-string id))]
              (response
                (emit! job-store (create-event event (read-string id) data)))
              (route/not-found "No such job"))))))

    (route/not-found "<h2>Still haven't found what you're lookin' for?</h2>")))

(defrecord Webserver [ip port connection job-store scheduler]
  component/Lifecycle

  (start [component]
    (log/info "Starting web server")

    (let [app (-> (app-routes job-store scheduler)
                  handler/api
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
