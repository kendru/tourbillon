(ns tourbillon.infr.mongo
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [monger.core :as mg]))

(defrecord MongoDbConn [host port database conn db]
  component/Lifecycle
  (start [component]
    (log/info "Starting MongoDB connection")
    (let [conn (mg/connect {:host host :port port})
          db (mg/get-db conn database)]
      (assoc component :conn conn
                       :db db)))

  (stop [component]
    (log/info "Stopping MongoDB connection")
    (mg/disconnect (:conn component))
    (assoc component :conn nil
                     :db nil)))

(defn make-connection [config]
  (map->MongoDbConn (select-keys config [:host :port :database])))
