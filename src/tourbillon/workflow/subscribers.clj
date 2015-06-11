(ns tourbillon.workflow.subscribers
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.client :as http]
            [taoensso.timbre :as log]
            [clojure.string :refer [lower-case]]
            [slingshot.slingshot :refer [throw+]]
            [tourbillon.template.core :refer [render-template]]))

(defprotocol SubscriberHandler
  (notify! [this subscriber data])
  (get-required-params [this]))

(def builtin-handlers
  {:log (reify
          SubscriberHandler
          (notify! [_ subscriber data] (log/info data))
          (get-required-params [_] nil))
   :email (reify
            SubscriberHandler
            (notify! [_ {:keys [recipient subject]} data]
              (log/info (str "emailing <" recipient ">: " subject ". DATA: " data)))
            (get-required-params [_] #{:recipient :subject :body}))
   :webhook (reify
              SubscriberHandler
              (notify! [_ {:keys [url method]} data]
                (log/info (str method " : " url " : " data))
                (if (= (lower-case method) "get")
                  (http/get url {:query-params data})
                  (http/post url {:body data})))
              (get-required-params [_] #{:url :method}))})

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
                (println "replacing" old-key-name "with" new-key "and template" template-id)
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
    (log/info "Notifying subscribers!")
    (when-let [missing-type (some (partial get-type-of-missing-handler this) subscribers)]
      (println "Missing subscriber :(" missing-type)
      (throw+ {:type ::no-handler :subscriber-type missing-type :message "No handler defined for subscriber type"}))
    (doseq [{:keys [type] :as subscriber} subscribers
            :let [handler (get-in this [:handlers (keyword type)])]]
      (notify! handler (-> subscriber
                           (dissoc :type)
                           (prepare-templates (:template-store this) data)) data))))

(defn new-subscriber-system []
  (map->DefaultSubscriberSystem {}))

