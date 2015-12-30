(ns tourbillon.www.utils)

(defn json-request? [req]
  (= "application/json"
     (get-in req [:headers "content-type"])))
