(ns tourbillon.utils)

(defn ^:dynamic uuid []
  (str (java.util.UUID/randomUUID)))

(defn ^:dynamic get-time []
  (int (/ (System/currentTimeMillis) 1000)))
