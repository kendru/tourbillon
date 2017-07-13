(ns tourbillon.workflow.handler.core)

(defprotocol SubscriberHandler
  (notify! [this subscriber data])
  (get-required-params [this]))
