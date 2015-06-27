(ns tourbillon.storage.event
  (:require [com.stuartsierra.component :as component]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [clojure.java.jdbc :as sql]
            [taoensso.timbre :as log]
            [clojure.set :refer [rename-keys]]
            [cheshire.core :as json]
            [tourbillon.utils :as utils]
            [tourbillon.event.core :refer [map->Event]]
            [tourbillon.storage.sql-helpers :refer [mk-connection-pool select-row]]))

(defn exc-inc-range
  "Gets a numeric range from start (exclusive) to end (inclusive),
  with an optional step parameter"
  [start end]
  (reverse (range end start -1)))

(defprotocol EventStore
  (store-event! [this event])
  (get-events [this timestamp]))

(defrecord InMemoryEventStore [db last-check-time]
  component/Lifecycle
  (start [component]
    (log/info "Starting local event store")
    component)

  (stop [component]
    (log/info "Stopping local event store")
    component)

  EventStore
  (store-event! [this event]
    (let [timestamp (:start event)
          db (:db this)]
      (swap! db update-in [timestamp] conj event)))

  (get-events [this timestamp]
    (let [{:keys [db]} this
          all-timestamps (exc-inc-range @last-check-time timestamp)
          events (mapcat #(get @db % (list)) all-timestamps)]
      (println "Getting events in " all-timestamps)
      (swap! db #(apply dissoc % all-timestamps))
      (reset! last-check-time timestamp)
      events)))

(defrecord MongoDBEventStore [mongo-opts db-name collection conn db]
  component/Lifecycle
  (start [component]
    (log/info "Starting MongoDB event store")
    (let [conn (mg/connect mongo-opts)]
      (assoc component :conn conn
                       :db (mg/get-db conn db-name))))

  (stop [component]
    (log/info "Stopping MongoDB event store")
    (mg/disconnect (:conn component))
    (assoc component :db nil :conn nil))

  EventStore
  (store-event! [this event]
    (let [timestamp (:start event)
         {:keys [db collection]} this]
      (mc/update db collection {:_id timestamp}
                               {$push {:events (into {} event)}}
                               {:upsert true})))

  (get-events [this timestamp]
    (let [{:keys [db collection]} this
          records (mc/find-and-modify db collection
                                      {:_id {$lte timestamp}}
                                      {}
                                      {:remove true})]
      (->> records
           (mapcat :events)
           (map map->Event)))))

(defrecord SQLEventStore [db-spec table conn]
  component/Lifecycle
  (start [component]
    (log/info "Starting SQL event store (" table ")")
    (assoc component :conn (mk-connection-pool db-spec)))

  (stop [component]
    (log/info "Stopping SQL event store(" table ")")
    (assoc component :conn nil))

  EventStore
  (store-event! [this event]
    (let [{:keys [id job-id start interval data]} event]
      (sql/insert! conn table {:id id
                               :job_id job-id
                               :start start
                               :interval interval
                               :data (json/generate-string data)})))

  (get-events [this timestamp]
    (let [query (str "UPDATE " (sql/quoted \" table)
                     " SET is_expired = true"
                     " WHERE \"start\" <= ?"
                     " AND is_expired = false"
                     " RETURNING id, job_id, \"start\", \"interval\", data")
          events (sql/query conn [query timestamp])]
      (when (seq events)
        (log/debug "Found events" timestamp events))
      (mapv (fn [row]
              (-> row
                  (update-in [:data] #(json/parse-string % true))
                  (rename-keys {:job_id :job-id})
                  map->Event))
            events))))

(defmulti new-event-store :type)

(defmethod new-event-store :local
  [{:keys [db last-check-time]}]
  (map->InMemoryEventStore {:db db
                            :last-check-time (atom (or last-check-time (utils/get-time)))}))

(defmethod new-event-store :mongodb
  [{:keys [mongo-opts db domain]}]
  (map->MongoDBEventStore {:mongo-opts mongo-opts
                           :db-name db
                           :collection domain}))

(defmethod new-event-store :sql
  [{:keys [db-spec domain]}]
  (map->SQLEventStore {:db-spec db-spec
                       :table domain}))
