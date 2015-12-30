(ns tourbillon.www.middleware
  (:require [ring.util.response :refer [response]]))

(defn without-api-key [handler]
  (fn [req]
    (let [resp (handler req)]
      (if (= 200 (:status resp))
        (let [body (:body resp)]
          (cond
           (map? body)
           (update-in resp [:body] #(dissoc % :api-key))
           
           (and (sequential? body)
                (every? map? body))
           (assoc resp :body
                  (map #(dissoc % :api-key) body))

           :else
           resp))
        resp))))
