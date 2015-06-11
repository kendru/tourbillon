(ns tourbillon.template.core
  (:require [clojure.core.memoize :as memo]
            [tourbillon.storage.object :as store]
            [stencil.core :as stencil]
            [stencil.parser :as parser]
            [slingshot.slingshot :refer [try+]]))

(defn valid-template? [text]
  (try+
    (parser/parse text)
    true
    (catch Object _
      false)))

(def malformed-template-response
  {:status 400
   :body {:status "error"
          :message "Malformed mustache template"}})

(defn create-template!
  "Stores the template and returns the id of the saved object"
  [template-store api-key text]
  {:pre [(valid-template? text)]}
  (get (store/create! template-store {:text text :api-key api-key :type "mustache"}) :id))

(defn render-template [template-store id data]
  (when-let [{:keys [text]} (store/find-by-id template-store id)]
    (stencil/render-string text data)))
