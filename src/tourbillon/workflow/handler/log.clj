(ns tourbillon.workflow.handler.log
  (:require [taoensso.timbre :as log]
            [tourbillon.workflow.handler.core :as handler]))

(defrecord LogHandler []
  handler/SubscriberHandler
  (notify! [_ _ data]
    (log/info data))

  (get-required-params [_]
    nil))

(defn handler []
  (LogHandler.))