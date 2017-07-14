(ns tourbillon.workflow.handler.twilio-sms
  (:require [taoensso.timbre :as log]
            [slingshot.slingshot :refer [try+ throw+]]
            [org.httpkit.client :as http]
            [tourbillon.workflow.handler.core :as handler]))

(defrecord TwilioSmsHandler [sid auth-token sender]
  handler/SubscriberHandler
  (notify! [_ subscriber data]
    (let [{:keys [recipient body]} subscriber]
      (try+
        (http/post
          (format "https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json" sid)
          {:timeout 1000
           :basic-auth [sid auth-token]
           :form-params {"From" sender "To" recipient "Body" body}}
          (fn [{:keys [status body error]}]
            (if error
              (log/warn "Error sending sms" error)
              (log/debug "Sent sms" recipient body))))
        (catch Exception e
          (log/error "Error sending sms" (.getMessage e))))))

  (get-required-params [_]
    #{:recipient :body}))


(defn handler [cfg]
  (map->TwilioSmsHandler cfg))