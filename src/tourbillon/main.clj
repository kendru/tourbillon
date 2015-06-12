(ns tourbillon.main
  (:gen-class)
  (:require [tourbillon.core :refer [system]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [environ.core :refer [env]]))

(log/set-config! [:appenders :spit :enabled?] true)
(log/set-config! [:shared-appender-config :spit-filename] (get env :log-file))

(defn -main
  "Start application with a given number of worker processes and optionally
  a webserver for a sample client application."
  [& args]
  (let [env (or (first args) "dev")]
    (log/info (str "Starting system in " env))
    (component/start
     (system {:app-env (get env :app-env)}))))
