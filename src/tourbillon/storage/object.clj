(ns tourbillon.storage.object
  (:require [com.stuartsierra.component :as component]
            [monger.core :as mg]
            [monger.collection :as mc]
            [taoensso.timbre :as log]
            [clojure.set :refer [rename-keys]]
            [tourbillon.utils :as utils]))

(defprotocol ObjectStore
  (find-by-id [this id])
  (create! [this obj])
  (update! [this obj update-fn]))

(defrecord InMemoryObjectStore [db serialize-fn unserialize-fn autoincrement]
  component/Lifecycle
  (start [component]
    (log/info "Starting in-memory object store")
    (assoc component :db db))

  (stop [component]
    (log/info "Stopping in-memory object store")
    (assoc component :db nil))

  ObjectStore
  (find-by-id [this id]
    (some-> @db
      (get id)
      unserialize-fn))

  (create! [this obj]
    (let [id (swap! autoincrement inc)
          obj (assoc obj :id id)]
      (swap! db assoc id (serialize-fn obj))
      obj))

  (update! [this obj update-fn]
    (let [id (:id obj)]
      (swap! db update-in [id] update-fn)
      (find-by-id this id))))

(defrecord MongoDBObjectStore [mongo-opts db-name collection serialize-fn unserialize-fn conn db]
  component/Lifecycle
  (start [component]
    (log/info "Starting MongoDB object store")
    (let [conn (mg/connect mongo-opts)]
      (assoc component :conn conn
                       :db (mg/get-db conn db-name))))

  (stop [component]
    (log/info "Stopping MongoDB object store")
    (mg/disconnect (:conn component))
    (assoc component :db nil :conn nil))

  ObjectStore
  (find-by-id [this id]
    (when-let [obj (mc/find-map-by-id (:db this) (:collection this) id)]
      (unserialize-fn (rename-keys obj {:_id :id}))))

  (create! [this obj]
    (let [id (utils/uuid)]
      (mc/insert (:db this) (:collection this) (assoc obj :_id id))
      (assoc obj :id id)))

  (update! [this obj update-fn]
    (when-let [retrieved (mc/find-map-by-id (:db this) (:collection this) (:id obj))]
      (let [updated (update-fn (rename-keys retrieved {:_id :id}))]
        (mc/update-by-id (:db this) (:collection this) (:id obj)
          (rename-keys updated {:id :_id}))
        updated))))

(defmulti new-object-store :type)

(defmethod new-object-store :local
  [{:keys [db serialize-fn unserialize-fn]}]
  (map->InMemoryObjectStore {:db db
                             :serialize-fn serialize-fn
                             :unserialize-fn unserialize-fn
                             :autoincrement (atom 0)}))

(defmethod new-object-store :mongodb
  [{:keys [mongo-opts db collection serialize-fn unserialize-fn]}]
  (map->MongoDBObjectStore {:mongo-opts mongo-opts
                            :db-name db
                            :collection collection
                            :serialize-fn serialize-fn
                            :unserialize-fn unserialize-fn}))
