(ns tourbillon.core
  (:gen-class)
  (:require [tourbillon.system :as system]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]))

(defn -main
  "Start application with a given number of worker processes and optionally
  a webserver for a sample client application."
  [& args]
  (log/info (str "Tourbillon is starting up"))
  (component/start (system/system)))
