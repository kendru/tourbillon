(ns tourbillon.infr.postgres
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc])
  (:import [com.jolbox.bonecp BoneCPDataSource]))

(defrecord PsqlConnectionPool [config]
  component/Lifecycle
  (start [component]
    (log/info "Starting database connection pool")
    (let [{:keys [url user password
                  init-pool-size
                  max-pool-size
                  partitions
                  idle-time]} config
          min-connections (inc (quot init-pool-size partitions))
          max-connections (inc (quot max-pool-size partitions))
          pooled-conn (doto (BoneCPDataSource.)
                        (.setDriverClass "org.postgresql.Driver")
                        (.setJdbcUrl url)
                        (.setUsername user)
                        (.setPassword password)
                        (.setMinConnectionsPerPartition min-connections)
                        (.setMaxConnectionsPerPartition max-connections)
                        (.setPartitionCount partitions)
                        (.setStatisticsEnabled true)
                        (.setIdleMaxAgeInMinutes idle-time))
          jdbc-conn {:datasource pooled-conn}]
      (assoc component :conn jdbc-conn)))
  (stop [component]
    (log/info "Stopping database connection pool")
    (try
      (.close (get-in component [:conn :datasource]))
      (catch Exception e
        (log/error e "Error closing db connection pool")))
    (dissoc component :conn)))

(defn make-connection-pool [config]
  (->PsqlConnectionPool config))
