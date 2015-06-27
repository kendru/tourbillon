(ns tourbillon.user
  (:require [tourbillon.core :as core]
            [tourbillon.event.core :as event]
            [tourbillon.schedule.core :as schedule]
            [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :refer [refresh]]))

(def system nil)

(defn init []
  (alter-var-root #'system
    (constantly (core/system {:app-env "dev"}))))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system
    (fn [s] (when s (component/stop s)))))

(defn go []
  (init)
  (start))

(defn reset []
  (stop)
  (refresh :after 'tourbillon.user/go))
