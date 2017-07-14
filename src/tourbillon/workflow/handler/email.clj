(ns tourbillon.workflow.handler.email
  (:require [taoensso.timbre :as log]
            [postal.core :as postal]
            [tourbillon.workflow.handler.core :as handler]))

            
(defrecord EmailHandler [host user password port sender]
  handler/SubscriberHandler
  (notify! [_ subscriber data]
    (let [{:keys [recipient subject body]} subscriber
          sender (or sender (:sender subscriber))
          opts {:user user
                :pass password
                :host host
                :port port}
          msg {:from sender
               :to recipient
               :subject subject
               :body body}
          result (postal/send-message opts msg)]
      (log/debug "Sent email" recipient result)))

  (get-required-params [_]
    #{:recipient :subject :body}))


(defn handler [cfg]
  (map->EmailHandler cfg))