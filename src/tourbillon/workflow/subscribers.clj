(ns tourbillon.workflow.subscribers
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.client :as http]
            [taoensso.timbre :as log]
            [clojure.string :refer [lower-case]]
            [environ.core :refer [env]]
            [slingshot.slingshot :refer [try+ throw+]]
            [postal.core :as postal]
            [org.httpkit.client :as http]
            [tourbillon.workflow.handler.core :as handler]
            [tourbillon.workflow.handler.webhook :as webhook]
            [tourbillon.template.core :refer [render-template]]))


(def builtin-handlers
  {:log (reify
          handler/SubscriberHandler
          (notify! [_ subscriber data] (log/info data))
          (get-required-params [_] nil))

   :email (reify
            handler/SubscriberHandler
            (notify! [_ {:keys [recipient sender subject body]
                         :or {sender (get env :smtp-sender)}} data]
              (let [result (postal/send-message {:user (get env :smtp-user)
                                      :pass (get env :smtp-pass)
                                      :host (get env :smtp-host)
                                      :port (Integer/parseInt (get env :smtp-port))}
                                     {:from sender
                                      :to recipient
                                      :subject subject
                                      :body body})]
                (log/debug "Sent email" recipient result)))
            (get-required-params [_] #{:recipient :subject :body}))

   :sms (reify
          handler/SubscriberHandler
          (notify! [_ {:keys [recipient body]} data]
            (try+
              (http/post (str "https://api.twilio.com/2010-04-01/Accounts/" (get env :twilio-sid) "/Messages.json")
                         {:timeout 1000
                          :basic-auth [(get env :twilio-sid) (get env :twilio-auth-token)]
                          :form-params {"From" (get env :twilio-sender)
                                        "To" recipient
                                        "Body" body}}
                         (fn [{:keys [status body error]}]
                          (if error
                            (log/warn "Error sending sms" error)
                            (log/debug "Sent sms" recipient body))))
              (catch Object e
                (log/error "Error sending sms" (.getMessage e)))))
          (get-required-params [_] #{:recipient :body}))

   :webhook (webhook/handler)})

(defn- get-type-of-missing-handler [subscriber-system {:keys [type]}]
  (when-not (get-in subscriber-system [:handlers (keyword type)])
    type))

(defn- prepare-templates
  "Takes entries in subscriber whose keys end in \"-template\" and
  whose values are a template id and replaces them with an entry
  whose key has the \"-template\" suffix removed and whose value is
  the result of applying the template to data."
  [subscriber template-store data]
  (let [templated-ks (filter #(.endsWith (name %) "-template")
                     (keys subscriber))]
    (reduce (fn [acc k]
              (let [template-id (get acc k)
                    old-key-name (name k)
                    new-key (keyword (subs old-key-name 0 (- (count old-key-name) 9)))]
                (-> acc
                    (assoc new-key (render-template template-store template-id data))
                    (dissoc k)))) 
            subscriber
            templated-ks)))

(defprotocol SubscriberSystem
  (notify-all! [this subscribers data]))

(defrecord DefaultSubscriberSystem [template-store handlers]
  component/Lifecycle
  (start [component]
    (log/info "Starting subscriber system")
    (comment "Need to implement plugin system"
      (log/info "Looking for subscriber plugins"))
    (assoc component :handlers builtin-handlers))

  (stop [component]
    (log/info "Stopping subscriber system")
    (assoc component :handlers nil))

  SubscriberSystem
  (notify-all! [this subscribers data]
    (log/debug "Notifying subscribers!")
    (when-let [missing-type (some (partial get-type-of-missing-handler this) subscribers)]
      (log/warn "Missing subscriber:" missing-type)
      (throw+ {:type ::no-handler
               :subscriber-type missing-type
               :message "No handler defined for subscriber type"}))
    (doseq [{:keys [type] :as subscriber} subscribers
            :let [handler (get-in this [:handlers (keyword type)])]]
      (handler/notify! handler
                       (-> subscriber
                           (dissoc :type)
                           (prepare-templates (:template-store this) data))
                       data))))

(defn new-subscriber-system []
  (map->DefaultSubscriberSystem {}))

