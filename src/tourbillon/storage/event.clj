(ns tourbillon.storage.event
  (:require [com.stuartsierra.component :as component]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [taoensso.timbre :as log]
            [clojure.set :refer [rename-keys]]
            [tourbillon.utils :as utils]
            [tourbillon.event.core :refer [map->Event]]
            [tourbillon.storage.key-value :as kv]))

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

(defmulti new-event-store :type)

(defmethod new-event-store :local
  [{:keys [db kv-store]}]
  (map->InMemoryEventStore {:db db
                            :kv-store kv-store}))

(defmethod new-event-store :mongodb
  [{:keys [mongo-opts db collection kv-store]}]
  (map->MongoDBEventStore {:mongo-opts mongo-opts
                           :db-name db
                           :collection collection
                           :kv-store kv-store}))
