(ns pacl.core)

(defn str-contains? [s key]
  (if (= (type key) java.lang.String)
    (.contains s key)
    (some #(.contains s %) key)))

(defn make-entry [& entry]
  (apply hash-map entry))

(defrecord Archive [resource entries comment]
  java.io.Closeable
  (close [this]
    "Closes associated resource.
     Should always be called when done."
    (.close resource)))

(defn make-archive [& {:keys [resource entries comment] :or {:comment ""}}]
  {:pre [resource entries]}
  (Archive. resource entries comment))
