(ns tourbillon.storage.object
  (:require [com.stuartsierra.component :as component]
            [tourbillon.infr.config :as config]
            [tourbillon.infr.serializer :as s]
            [monger.collection :as mc]
            [clojure.java.jdbc :as sql]
            [taoensso.timbre :as log]
            [clojure.set :refer [rename-keys]]
            [tourbillon.utils :as utils]
            [tourbillon.storage.sql-helpers :refer [select-row]]))


(defprotocol ObjectStore
  (find-by-id [this id])
  (create! [this obj])
  (update! [this obj update-fn]))

(defrecord InMemoryObjectStore [autoincrement atom-db]
  component/Lifecycle
  (start [component]
    (log/info "Starting in-memory object store")
    (assoc component :atom-db atom-db))

  (stop [component]
    (log/info "Stopping in-memory object store")
    (assoc component :db nil))

  ObjectStore
  (find-by-id [this id]
    (let [db (:db atom-db)]
      (some-> @db
        (get id))))

  (create! [this obj]
    (let [db (:db atom-db)
          id (or (:id obj) (swap! autoincrement inc))
          obj (assoc obj :id id)]
      (swap! db assoc id obj)
      obj))

  (update! [this obj update-fn]
    (let [db (:db atom-db)
          id (:id obj)]
      (swap! db update-in [id] update-fn)
      (find-by-id this id))))

;; Note that this object store does not encrypt the data that it receives
(defrecord MongoDBObjectStore [collection mongo]
  component/Lifecycle
  (start [component]
    (log/info (str "Starting MongoDB object store (" collection ")"))
    component)

  (stop [component]
    (log/info (str "Stopping MongoDB object store (/" collection ")"))
    component)

  ObjectStore
  (find-by-id [this id]
    (let [{:keys [mongo collection]} this
          {:keys [:db]} mongo]
      (when-let [obj (mc/find-map-by-id db collection id)]
        (rename-keys obj {:_id :id}))))

  (create! [this obj]
    (let [{:keys [mongo collection]} this
          {:keys [:db]} mongo
          id (or (:id obj) (utils/uuid))]
      (mc/insert db collection (-> obj (assoc :_id id) (dissoc :id)))
      (assoc obj :id id)))

  (update! [this obj update-fn]
    (let [{:keys [mongo collection]} this
          {:keys [:db]} mongo
          {:keys [:id]} obj]
      (when-let [retrieved (mc/find-map-by-id db collection id)]
        (let [updated (update-fn (rename-keys retrieved {:_id :id}))]
          (mc/update-by-id db collection id
            (rename-keys updated {:id :_id}))
          updated)))))

;; We could probably be smarter about normalizing the data model and only encrypting
;; the user-supplied data
(defrecord PostgresObjectStore [table serializer postgres]
  component/Lifecycle
  (start [component]
    (log/info "Starting Postgres object store (" table ")")
    component)    

  (stop [component]
    (log/info "Stopping Postgres object store (" table ")")
    component)

  ObjectStore
  (find-by-id [this id]
    (let [{:keys [conn]} postgres]
      (some-> (select-row conn table id)
              (get :data)
              (->> (s/deserialize serializer))
              (assoc :id id))))

  (create! [this {:keys [id] :as obj}]
    (let [{:keys [conn]} postgres
          id (or id (utils/uuid))]
      (sql/insert! conn table
        {:id id
         :data (s/serialize serializer (dissoc obj :id))})
      (assoc obj :id id)))

  (update! [this {:keys [id] :as obj} update-fn]
    (sql/with-db-transaction [t-conn (:conn postgres)]
      (when-let [{:keys [data]} (select-row t-conn table id)]
        (let [updated (update-fn (s/deserialize serializer data))]
          (sql/update! t-conn table
                       {:data (s/serialize serializer updated)}
                       ["id = ?" id])
          updated)))))

(defmulti make-store
  (fn [cfg schema domain]
    (get-in cfg [:global :object-store-type])))

(defmethod make-store :local
  [_ _ _]
  (map->InMemoryObjectStore
    {:autoincrement (atom 0)}))

(defmethod make-store :mongo
  [_ _ domain]
  (map->MongoDBObjectStore
    {:collection domain}))
                            

(defmethod make-store :postgres
  [_ _ domain]
  (map->PostgresObjectStore
    {:table domain}))
