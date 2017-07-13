(ns tourbillon.storage.object
  (:require [com.stuartsierra.component :as component]
            [tourbillon.storage.encryption :refer [serialize deserialize]]
            [buddy.core.nonce :as nonce]
            [monger.core :as mg]
            [monger.collection :as mc]
            [clojure.java.jdbc :as sql]
            [taoensso.timbre :as log]
            [clojure.set :refer [rename-keys]]
            [tourbillon.utils :as utils]
            [tourbillon.storage.sql-helpers :refer [mk-connection-pool select-row]]))


(defprotocol ObjectStore
  (find-by-id [this id])
  (create! [this obj])
  (update! [this obj update-fn]))

(defrecord InMemoryObjectStore [db schema autoincrement]
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
      (get id)))

  (create! [this obj]
    (let [id (or (:id obj) (swap! autoincrement inc))
          obj (assoc obj :id id)]
      (swap! db assoc id obj)
      obj))

  (update! [this obj update-fn]
    (let [id (:id obj)]
      (swap! db update-in [id] update-fn)
      (find-by-id this id))))

;; Note that this object store does not encrypt the data that it receives
(defrecord MongoDBObjectStore [mongo-opts db-name collection schema conn db]
  component/Lifecycle
  (start [component]
    (log/info (str "Starting MongoDB object store (" db-name "/" collection ")"))
    (let [conn (mg/connect mongo-opts)]
      (assoc component :conn conn
                       :db (mg/get-db conn db-name))))

  (stop [component]
    (log/info (str "Stopping MongoDB object store (" db-name "/" collection ")"))
    (mg/disconnect (:conn component))
    (assoc component :db nil :conn nil))

  ObjectStore
  (find-by-id [this id]
    (when-let [obj (mc/find-map-by-id (:db this) (:collection this) id)]
      (rename-keys obj {:_id :id})))

  (create! [this obj]
    (let [id (or (:id obj) (utils/uuid))]
      (mc/insert (:db this) (:collection this) (-> obj (assoc :_id id) (dissoc :id)))
      (assoc obj :id id)))

  (update! [this obj update-fn]
    (when-let [retrieved (mc/find-map-by-id (:db this) (:collection this) (:id obj))]
      (let [updated (update-fn (rename-keys retrieved {:_id :id}))]
        (mc/update-by-id (:db this) (:collection this) (:id obj)
          (rename-keys updated {:id :_id}))
        updated))))

;; We could probably be smarter about normalizing the data model and only encrypting
;; the user-supplied data
(defrecord SQLObjectStore [db-spec table conn]
  component/Lifecycle
  (start [component]
    (log/info "Starting SQL object store (" table ")")
    (assoc component :conn (mk-connection-pool db-spec)))

  (stop [component]
    (log/info "Stopping SQL object store (" table ")")
    (assoc component :conn nil))

  ObjectStore
  (find-by-id [this id]
    (let [{:keys [data iv]} (select-row conn table id)]
      (-> data
          (deserialize iv)
          (assoc :id id))))

  (create! [this obj]
    (let [id (or (:id obj) (utils/uuid))
          iv (nonce/random-bytes 12)]
      (sql/insert! conn table {:id id
                               :data (serialize (dissoc obj :id) iv)
                               :iv iv})
      (assoc obj :id id)))

  (update! [this obj update-fn]
    (sql/with-db-transaction [t-conn conn]
      (when-let [{:keys [data iv]} (select-row t-conn table (:id obj))]
        (let [updated (update-fn (deserialize data iv))]
          (sql/update! t-conn table {:data (serialize updated iv)}
                       ["id = ?" (:id obj)])
          updated)))))

(defmulti new-object-store :type)

(defmethod new-object-store :local
  [{:keys [db schema]}]
  (map->InMemoryObjectStore {:db db
                             :schema schema
                             :autoincrement (atom 0)}))

(defmethod new-object-store :mongodb
  [{:keys [mongo-opts db domain schema]}]
  (map->MongoDBObjectStore {:mongo-opts mongo-opts
                            :db-name db
                            :collection domain
                            :schema schema}))

(defmethod new-object-store :sql
  [{:keys [db-spec domain schema]}]
  (map->SQLObjectStore {:db-spec db-spec
                        :table domain
                        :schema schema}))
