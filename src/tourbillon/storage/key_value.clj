(ns tourbillon.storage.key-value
  (:require [com.stuartsierra.component :as component]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [taoensso.timbre :as log]
            [clojure.set :refer [rename-keys]]
            [tourbillon.utils :as utils]
            [tourbillon.event.core :refer [map->Event]]))

(defprotocol KVStore
  (get-val [this k])
  (set-val! [this k v]))

(defrecord InMemoryKVStore [db]
  component/Lifecycle
  (start [component]
    (log/info "Starting local key/value store")
    component)

  (stop [component]
    (log/info "Stopping local key/value store")
    (assoc component :db nil))

  KVStore
  (get-val [this k]
    (get @(:db this) k))

  (set-val! [this k v]
    (swap! (:db this) assoc k v)))

(defrecord MongoDBKVStore [mongo-opts db-name collection conn db]
  component/Lifecycle
  (start [component]
    (log/info "Starting MongoDB key/value store")
    (let [conn (mg/connect mongo-opts)]
      (assoc component :conn conn
                       :db (mg/get-db conn db-name))))

  (stop [component]
    (log/info "Stopping MongoDB key/value store")
    (mg/disconnect (:conn component))
    (assoc component :db nil :conn nil))

  KVStore
  (get-val [this k]
    (get (mc/find-map-by-id (:db this) (:collection this) (str k)) :value))

  (set-val! [this k v]
    (mc/update (:db this) (:collection this) {:_id (str k)} {:value v} {:upsert true})))

(defmulti new-kv-store :type)

(defmethod new-kv-store :local
  [{:keys [db]}]
  (map->InMemoryKVStore {:db db}))

(defmethod new-kv-store :mongodb
  [{:keys [mongo-opts db collection]}]
  (map->MongoDBKVStore {:mongo-opts mongo-opts
                        :db-name db
                        :collection collection}))