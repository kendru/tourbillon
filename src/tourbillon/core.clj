(ns tourbillon.core
  (:require [tourbillon.www.core :refer [new-server]]
            [tourbillon.storage.key-value :refer [new-kv-store]]
            [tourbillon.storage.object :refer [new-object-store]]
            [tourbillon.storage.event :refer [new-event-store]]
            [tourbillon.schedule.core :refer [new-scheduler]]
            [tourbillon.workflow.subscribers :refer [new-subscriber-system]]
            [tourbillon.workflow.jobs :refer [map->Job map->Workflow]]
            [overtone.at-at :refer [mk-pool]]
            [com.stuartsierra.component :as component]))

(def mongo-opts {:host "127.0.0.1"})

(defn system [config-options]
  (let [{:keys [ip port]} config-options]
    (component/system-map
      :config-options config-options
      ; :kv-store (new-kv-store {:type :local
      ;                          :db (atom {})})
      ; :job-store (new-object-store {:type :local
      ;                               :db (atom {})
      ;                               :serialize-fn (partial into {})
      ;                               :unserialize-fn map->Job})
      ; :workflow-store (new-object-store {:type :local
      ;                                    :db (atom {})
      ;                                    :serialize-fn (partial into {})
      ;                                    :unserialize-fn map->Workflow})
      ; :event-store (component/using
      ;                (new-event-store {:type :local :db (atom {})})
      ;                [:kv-store])
      :kv-store (new-kv-store {:type :mongodb
                               :db "tourbillon"
                               :collection "kv"
                               :mongo-opts mongo-opts})
      :job-store (new-object-store {:type :mongodb
                                    :db "tourbillon"
                                    :collection "jobs"
                                    :mongo-opts mongo-opts
                                    :serialize-fn (partial into {})
                                    :unserialize-fn map->Job})
      :workflow-store (new-object-store {:type :mongodb
                                         :db "tourbillon"
                                         :collection "workflows"
                                         :mongo-opts mongo-opts
                                         :serialize-fn (partial into {})
                                         :unserialize-fn map->Workflow})
      :account-store (new-object-store {:type :mongodb
                                        :db "tourbillon"
                                        :collection "accounts"
                                        :mongo-opts mongo-opts
                                        :serialize-fn identity
                                        :unserialize-fn identity})
      :template-store (new-object-store {:type :mongodb
                                         :db "tourbillon"
                                         :collection "templates"
                                         :mongo-opts mongo-opts})
      :event-store (component/using
                     (new-event-store {:type :mongodb
                                       :db "tourbillon"
                                       :collection "events"
                                       :mongo-opts mongo-opts})
                     [:kv-store])
      :subscriber-system (component/using
                           (new-subscriber-system)
                           [:template-store])
      :scheduler (component/using
                   (new-scheduler 1000 (mk-pool))
                   [:job-store :event-store :subscriber-system])
      :webserver (component/using
                   (new-server ip port)
                   [:job-store :account-store :template-store :scheduler]))))
