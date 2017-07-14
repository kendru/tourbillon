(ns tourbillon.workflow.subscribers
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [tourbillon.infr.config :as config]
            [tourbillon.workflow.handler.core :as handler]
            [tourbillon.workflow.handler.webhook :as webhook]
            [tourbillon.workflow.handler.email :as email]
            [tourbillon.workflow.handler.twilio-sms :as twilio-sms]
            [tourbillon.workflow.handler.log :as log-handler]
            [tourbillon.template.core :as template]))


(defn builtin-handlers [cfg]
  {:log (log-handler/handler)
   :webhook (webhook/handler)
   :email (email/handler (config/smtp cfg))
   :twilio-sms (twilio-sms/handler (config/twilio cfg))})


(defprotocol SubscriberSystem
  (notify-all! [this subscribers data]))


(defrecord DefaultSubscriberSystem [config handlers template-store]
  component/Lifecycle
  (start [component]
    (log/info "Starting subscriber system")
    (comment "Need to implement plugin system"
      (log/info "Looking for subscriber plugins"))
    (assoc component :handlers (builtin-handlers config)))

  (stop [component]
    (log/info "Stopping subscriber system")
    (assoc component :handlers nil))

  SubscriberSystem
  (notify-all! [this subscribers data]
    (doseq [{:keys [type]
             :or {type :none}
             :as subscriber} subscribers
            :let [handler (get handlers (keyword type))]]
      (if (some? handler)
        (handler/notify!
          handler
          (-> subscriber
              (dissoc :type)
              (template/prepare-templates template-store data))
          data)
        (log/error "No handler for subscriber type:" type)))))


(defn make-system [cfg]
  (map->DefaultSubscriberSystem {:config cfg}))