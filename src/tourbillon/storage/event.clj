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
            [tourbillon.storage.key-value :as kv]
            [tourbillon.storage.sql-helpers :refer [mk-connection-pool select-row]]))

(defn exc-inc-range
  "Gets a numeric range from start (exclusive) to end (inclusive),
  with an optional step parameter"
  [start end]
  (reverse (range end start -1)))

(defn init-last-check-time!
  "Initialize the :last-check-time key in the key/value store only if it is not
  already set"
  [kv-store]
  (when-not (kv/get-val kv-store :last-check-time)
    (kv/set-val! kv-store :last-check-time (utils/get-time))))

(defprotocol EventStore
  (store-event! [this event])
  (get-events [this timestamp]))

(defrecord InMemoryEventStore [db kv-store]
  component/Lifecycle
  (start [component]
    (log/info "Starting local event store")
    (init-last-check-time! kv-store)
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
    (let [{:keys [db kv-store]} this
          all-timestamps (exc-inc-range (kv/get-val kv-store :last-check-time) timestamp)
          events (mapcat #(get @db % (list)) all-timestamps)]
      (println "Getting events in " all-timestamps)
      (swap! db #(apply dissoc % all-timestamps))
      (kv/set-val! kv-store :last-check-time timestamp)
      events)))

(defrecord MongoDBEventStore [mongo-opts db-name collection kv-store conn db]
  component/Lifecycle
  (start [component]
    (log/info "Starting MongoDB event store")
    (init-last-check-time! kv-store)
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
    (let [{:keys [db kv-store collection]} this
          all-timestamps (exc-inc-range (kv/get-val kv-store :last-check-time) timestamp)
          events (into []
                   (mapcat (fn [timestamp]
                             (when-let [record (mc/find-and-modify db collection {:_id timestamp} {} {:remove true})]
                               (map map->Event (:events record)))) all-timestamps))]
      (kv/set-val! kv-store :last-check-time timestamp)
      events)))

(defrecord SQLEventStore [db-spec table kv-store conn]
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
    (let [last-check (kv/get-val kv-store :last-check-time)
          query (str "UPDATE " (sql/quoted \" table)
                     " SET is_expired = true"
                     " WHERE \"start\" >= ? AND \"start\" <= ?"
                     " AND is_expired = false"
                     " RETURNING id, job_id, \"start\", \"interval\", data")
          events (sql/query conn [query last-check timestamp])
          _ (when (seq events) (println "Found events " events))]
      (kv/set-val! kv-store :last-check-time timestamp)
      (mapv (fn [row]
              (-> row
                  (update-in [:data] #(json/parse-string % true))
                  (rename-keys {:job_id :job-id})
                  map->Event))
            events))))

(defmulti new-event-store :type)

(defmethod new-event-store :local
  [{:keys [db kv-store]}]
  (map->InMemoryEventStore {:db db
                            :kv-store kv-store}))

(defmethod new-event-store :mongodb
  [{:keys [mongo-opts db domain kv-store]}]
  (map->MongoDBEventStore {:mongo-opts mongo-opts
                           :db-name db
                           :collection domain
                           :kv-store kv-store}))

(defmethod new-event-store :sql
  [{:keys [db-spec domain kv-store]}]
  (map->SQLEventStore {:db-spec db-spec
                       :table domain
                       :kv-store kv-store}))
