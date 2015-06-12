(ns tourbillon.core
  (:require [tourbillon.www.core :refer [new-server]]
            [tourbillon.storage.key-value :refer [new-kv-store]]
            [tourbillon.storage.object :refer [new-object-store]]
            [tourbillon.storage.event :refer [new-event-store]]
            [tourbillon.schedule.core :refer [new-scheduler]]
            [tourbillon.workflow.subscribers :refer [new-subscriber-system]]
            [tourbillon.workflow.jobs :refer [map->Job map->Workflow]]
            [environ.core :refer [env]]
            [overtone.at-at :refer [mk-pool]]
            [com.stuartsierra.component :as component]))

(def mongo-opts {:host "127.0.0.1"})

(def store-opts
  {"local" {:type :local
            :db (atom {})}
   "mongodb" {:type :mongodb
              :db (get env :mongo-db)
              :mongo-opts {:host (get env :mongo-host)}}})

(defn system [config-options]
  (let [{:keys [app-env]} config-options
        {:keys [kv-store-type object-store-type web-ip web-port]} env]
    (component/system-map
      :config-options config-options
      :kv-store (new-kv-store (merge (get store-opts kv-store-type) {:collection "kv"}))
      :job-store (new-object-store (merge (get store-opts object-store-type)
                                          {:collection "jobs"
                                           :serialize-fn (partial into {})
                                           :unserialize-fn map->Job}))
      :workflow-store (new-object-store (merge (get store-opts object-store-type)
                                               {:collection "workflows"
                                                :serialize-fn (partial into {})
                                                :unserialize-fn map->Job}))
      :account-store (new-object-store (merge (get store-opts object-store-type)
                                              {:collection "accounts"}))
      :template-store (new-object-store (merge (get store-opts object-store-type)
                                               {:collection "templates"}))
      :event-store (component/using
                     (new-event-store (merge (get store-opts object-store-type)
                                             {:collection "events"}))
                     [:kv-store])
      :subscriber-system (component/using
                           (new-subscriber-system)
                           [:template-store])
      :scheduler (component/using
                   (new-scheduler 1000 (mk-pool))
                   [:job-store :event-store :subscriber-system])
      :webserver (component/using
                   (new-server {:ip (get env :web-ip)
                                :port (Integer/parseInt (get env :web-port))}) ;; TODO: replace IP and port
                   [:job-store :account-store :template-store :scheduler]))))
