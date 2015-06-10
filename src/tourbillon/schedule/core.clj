(ns tourbillon.schedule.core
  (:require [tourbillon.event.core :refer :all]
            [tourbillon.storage.event :as event]
            [tourbillon.workflow.jobs :refer [emit!]]
            [com.stuartsierra.component :as component]
            [overtone.at-at :as at]
            [taoensso.timbre :as log]
            [tourbillon.utils :as utils]))

(defmulti send-event!
  (fn [scheduler event]
    (if (is-immediate? event)
      :immediate
      :delayed)))

(defmethod send-event! :immediate [scheduler event]
  (let [job-store (:job-store scheduler)]
    (log/info ["processing event now" event])
    (emit! job-store event)))

(defmethod send-event! :delayed [scheduler event]
  (let [event-store (:event-store scheduler)]
    (log/info ["processing event later" event])
    (event/store-event! event-store event)))

(defn process-events!
  "Gets any new events from the event store and sends them to their respective jobs"
  [{:keys [job-store event-store]}]
  (let [events (event/get-events event-store (utils/get-time))]
    (when-not (empty? events)
      (doseq [event events]
        (emit! job-store event)
        (when (is-recurring? event)
          (event/store-event! event-store (next-interval event)))))))

(defrecord Scheduler [poll-freq thread-pool job-store event-store scheduler]
  component/Lifecycle

  (start [component]
    (log/info "Starting scheduler")
    (let [sched (at/every poll-freq #(process-events! component) thread-pool)]
      (assoc component :scheduler sched
                       :job-store job-store
                       :event-store event-store)))

  (stop [component]
    (log/info "Stopping scheduler")
        (at/stop scheduler)
        (dissoc component :scheduler :job-store :event-store)))

(defn new-scheduler [poll-freq thread-pool]
  (map->Scheduler {:poll-freq poll-freq
                   :thread-pool thread-pool}))
