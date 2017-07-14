(ns tourbillon.template.core
  (:require [tourbillon.storage.object :as store]
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

(defn render [template-store id data]
  (when-let [{:keys [text]} (store/find-by-id template-store id)]
    (stencil/render-string text data)))

(defn prepare-templates
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
                    (assoc new-key (render template-store template-id data))
                    (dissoc k)))) 
            subscriber
            templated-ks)))