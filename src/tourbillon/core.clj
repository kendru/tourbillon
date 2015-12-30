(ns tourbillon.core
  (:require [tourbillon.www.core :refer [new-server]]
            [tourbillon.storage.object :refer [new-object-store]]
            [tourbillon.storage.event :refer [new-event-store]]
            [tourbillon.schedule.core :refer [new-scheduler]]
            [tourbillon.workflow.subscribers :refer [new-subscriber-system]]
            [tourbillon.domain :refer [Job Workflow]]
            [environ.core :refer [env]]
            [overtone.at-at :refer [mk-pool]]
            [com.stuartsierra.component :as component]))

(def mongo-opts {:host "127.0.0.1"})

(defn get-store-opts
  ([kind] get-store-opts kind {})
  ([kind additional]
    (merge
      (condp = kind
        "local" {:type :local
                 :db (atom {})}
        "mongodb" {:type :mongodb
                   :db (get env :mongo-db)
                   :mongo-opts {:host (get env :mongo-host)}}
        "sql" {:type :sql
               :db-spec {:classname (get env :sql-classname)
                         :subprotocol (get env :sql-subprotocol)
                         :subname (str "//"
                                       (get env :sql-host) ":"
                                       (get env :sql-port) "/"
                                       (get env :sql-database))
                         :user (get env :sql-user)
                         :password (get env :sql-password)}})
     additional)))

(defn system [config-options]
  (let [{:keys [app-env]} config-options
        {:keys [kv-store-type object-store-type event-store-type web-ip web-port]} env]
    (component/system-map
      :config-options config-options
      :job-store (new-object-store (get-store-opts object-store-type
                                                   {:domain "jobs"
                                                    :schema Job}))
      :workflow-store (new-object-store (get-store-opts object-store-type
                                                        {:domain "workflows"
                                                         :schema Workflow}))
      :account-store (new-object-store (get-store-opts object-store-type
                                                       {:domain "accounts"}))
      :template-store (new-object-store (get-store-opts object-store-type
                                                        {:domain "templates"}))
      :event-store (new-event-store (get-store-opts event-store-type
                                                    {:domain "events"}))
      :subscriber-system (component/using
                           (new-subscriber-system)
                           [:template-store])
      :scheduler (component/using
                   (new-scheduler 1000 (mk-pool))
                   [:job-store :event-store :subscriber-system])
      :webserver (component/using
                   (new-server {:ip (get env :web-ip)
                                :port (Integer/parseInt (get env :web-port))}) ;; TODO: replace IP and port
                   [:job-store :workflow-store :account-store :template-store :scheduler :subscriber-system]))))
