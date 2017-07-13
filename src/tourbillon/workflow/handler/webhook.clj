(ns tourbillon.workflow.handler.webhook
  (:require [org.httpkit.client :as http]
            [taoensso.timbre :as log]
            [clojure.string :as string]
            [cheshire.core :as json]
            [ring.util.codec :as urlencode]
            [tourbillon.workflow.handler.core :as handler]))


(defn normalize-kw [s]
  (if (keyword? s)
    s
    (-> s name string/lower-case keyword)))


(defn obtain-oauth2-token [auth]
  (let [{:keys [client_id client_secret auth_token_endpoint]} auth
        resp @(http/post auth_token_endpoint
                        {:basic-auth [client_id client_secret]
                         :form-params {:grant_type "client_credentials"}})
        body (json/parse-string (:body resp))]
    (get body "access_token" "INVALID")))


(defn with-auth [req auth]
  (case (normalize-kw (get auth :type :none))
    :basic (assoc req :basic-auth [(:username auth) (:password auth)])
    :oauth2 (assoc req :oauth-token (obtain-oauth2-token auth))
    req))


(defn with-headers [req subscriber default-headers]
  (assoc req :headers
         (cond-> default-headers
           (= :post (:method req)) (assoc "Content-Type" (:content-type subscriber)))))


(defmulti request-body
  (fn [content-type data] content-type))

(defmethod request-body "application/json"
  [_ data]
  (json/generate-string data))

(defmethod request-body "application/x-www-form-urlencoded"
  [_ data]
  (urlencode/form-encode data))

(defmethod request-body "application/edn"
  [_ data]
  (pr-str data))

(defmethod request-body "text/plain"
  [_ data]
  (str data))

(defmethod request-body :default
  [_ data]
  (str data))


(defn with-data [req subscriber req-data]
  (let [{:keys [method content-type]} subscriber]
    (if (= :post method)
      (assoc req :body (request-body content-type req-data))
      (assoc req :query-params req-data))))


(defn coerce-subscriber [sub]
  (-> sub
      (update-in [:method] (fnil normalize-kw :post))
      (update-in [:body] #(or % {}))
      (update-in [:content-type] #(or % "application/json"))
      (update-in [:auth] #(or % {:type :none}))))


(defrecord WebhookHandler []
  handler/SubscriberHandler
  (notify! [_ subscriber data]
    (let [subscriber (coerce-subscriber subscriber)
          {:keys [url method auth headers body content-type]} subscriber
          req-data (merge body data)]
      (http/request (-> {:url url
                         :method method}
                        (with-headers subscriber headers)
                        (with-auth auth)
                        (with-data subscriber req-data)))))
  (get-required-params [_] #{:url}))


(defn handler []
  (WebhookHandler.))

