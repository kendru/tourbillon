(ns tourbillon.storage.object
  (:require [com.stuartsierra.component :as component]
            [monger.core :as mg]
            [monger.collection :as mc]
            [clojure.java.jdbc :as sql]
            [cheshire.core :as json]
            [taoensso.timbre :as log]
            [clojure.set :refer [rename-keys]]
            [tourbillon.utils :as utils]
            [tourbillon.storage.sql-helpers :refer [mk-connection-pool select-row]]))

(defn- parse-with-kw [string]
  (json/parse-string string true))

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
      (unserialize-fn (rename-keys obj {:_id :id}))))

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

(defrecord SQLObjectStore [db-spec table serialize-fn unserialize-fn conn]
  component/Lifecycle
  (start [component]
    (log/info "Starting SQL object store (" table ")")
    (assoc component :conn (mk-connection-pool db-spec)))
  
  (stop [component]
    (log/info "Stopping SQL object store (" table ")")
    (assoc component :conn nil))

  ObjectStore
  (find-by-id [this id]
    (some-> (select-row conn table id)
            (get :data)
            parse-with-kw
            (assoc :id id)
            unserialize-fn))

  (create! [this obj]
    (let [id (or (:id obj) (utils/uuid))]
      (sql/insert! conn table {:id id :data (-> obj serialize-fn (dissoc :id) json/generate-string)})
      (assoc obj :id id)))

  (update! [this obj update-fn]
    (sql/with-db-transaction [t-conn conn]
      (when-let [retrieved (select-row t-conn table (:id obj))]
        (let [updated (-> retrieved
                          (get :data)
                          parse-with-kw
                          unserialize-fn
                          update-fn)]
          (sql/update! t-conn table {:data (-> updated serialize-fn json/generate-string)}
                       ["id = ?" (:id obj)])
          updated)))))

(defmulti new-object-store :type)

(defmethod new-object-store :local
  [{:keys [db serialize-fn unserialize-fn]
    :or {serialize-fn identity
         unserialize-fn identity}}]
  (map->InMemoryObjectStore {:db db
                             :serialize-fn serialize-fn
                             :unserialize-fn unserialize-fn
                             :autoincrement (atom 0)}))

(defmethod new-object-store :mongodb
  [{:keys [mongo-opts db domain serialize-fn unserialize-fn]
    :or {serialize-fn identity
         unserialize-fn identity}}]
  (map->MongoDBObjectStore {:mongo-opts mongo-opts
                            :db-name db
                            :collection domain
                            :serialize-fn serialize-fn
                            :unserialize-fn unserialize-fn}))

(defmethod new-object-store :sql
  [{:keys [db-spec domain serialize-fn unserialize-fn]
    :or {serialize-fn identity
         unserialize-fn identity}}]
  (map->SQLObjectStore {:db-spec db-spec
                        :table domain
                        :serialize-fn serialize-fn
                        :unserialize-fn unserialize-fn}))
