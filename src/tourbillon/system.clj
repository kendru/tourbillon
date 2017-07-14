(ns tourbillon.system
  (:require [tourbillon.infr.config :as config]
            [tourbillon.infr.postgres :as postgres]
            [tourbillon.infr.mongo :as mongo]
            [tourbillon.infr.atom-db :as atom-db]
            [tourbillon.infr.serializer :as serializer]
            [tourbillon.infr.jwt :as jwt]
            [tourbillon.www.core :as www]
            [tourbillon.storage.object :as object-store]
            [tourbillon.storage.event :as event-store]
            [tourbillon.schedule.core :as scheduler]
            [tourbillon.workflow.subscribers :as subscribers]
            [tourbillon.domain :refer [Job Workflow]]
            [overtone.at-at :as at-at]
            [com.stuartsierra.component :as component]))

(defn profile
  "Retrieve the current application profile. Must be one of: :dev, :prod, or :test"
  []
  {:post [(contains? #{:dev :prod :test} %)]}
  (-> (System/getenv "APP_ENV")
      (or "dev")
      (.toLowerCase)
      keyword))


;; TODO: create system entries for any infrastructure dependency and require all of
;; them in components that may use them (e.g. object store uses postgres and mongo)
;; but if no config is provided for an infrastructure dependency, a dummy instance is
;; returned; and a store may only use the infrastructure dependencies that it is
;; interested in.
(defn system
  "Instantiate a system of all components"
  []
  (let [cfg (config/load-config (profile))]
    (component/system-map
      :atom-db (atom-db/make-db cfg)

      :serializer (serializer/make-serializer (config/serializer cfg))

      :signer (jwt/make-signer (config/jwt cfg))
      
      :postgres (postgres/make-connection-pool (config/postgres cfg))
      
      :mongo (mongo/make-connection (config/mongo cfg))

      :job-store (component/using
                  (object-store/make-store cfg Job "jobs")
                  [:atom-db :postgres :mongo :serializer])

      :workflow-store (component/using
                       (object-store/make-store cfg Workflow "workflows")
                       [:atom-db :postgres :mongo :serializer])

      :account-store (component/using
                      (object-store/make-store cfg nil "accounts")
                      [:atom-db :postgres :mongo :serializer])

      :template-store (component/using
                       (object-store/make-store cfg nil "templates")
                       [:atom-db :postgres :mongo :serializer])

      :event-store (component/using
                    (event-store/make-store cfg)
                    [:atom-db :postgres :mongo :serializer])

      :subscriber-system (component/using
                           (subscribers/make-system cfg)
                           [:template-store])

      :scheduler (component/using
                   (scheduler/make-scheduler 1000 (at-at/mk-pool))
                   [:job-store :event-store :subscriber-system])

      :webserver (component/using
                   (www/new-server (config/web cfg))
                   [:job-store
                    :workflow-store
                    :account-store
                    :template-store
                    :scheduler
                    :subscriber-system
                    :signer]))))
