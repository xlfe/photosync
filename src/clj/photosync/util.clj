(ns photosync.util
  (:require
    [camel-snake-kebab.core :as csk]
    [clojure.core.match :refer [match]])
  (:import
    [java.security NoSuchAlgorithmException MessageDigest]))

(defn discard-nils [a b]
  (match [a b]
         [_ nil] a
         :else b))

(def safe-merge (partial merge-with discard-nils))

(def ->kebab-case (memoize csk/->kebab-case))

(defn unqualified-name
  "Returns the non-namespaced part of a symbol."
  [sym]
  (let [name (str sym)
        idx (.lastIndexOf name "/")]
    (.substring name (inc (if (neg? idx) (.lastIndexOf name ".") idx)))))

(defn class-name-vec
  "Takes a class and returns vector of [ns localname]."
  [clz]
  (let [name (.getName clz)
        idx (.lastIndexOf name ".")]
    (if (neg? idx)
      ["" name]
      [(.substring name 0 idx) (.substring name (inc idx))])))

(defn array-of
  "Creates a new array of given type and fills it with the elements of
  given coll, each passed through f. Returns array."
  [type f xs]
  (->> xs (mapv f) (into-array type)))

(defn is-array*
  [t] (let [t (type (t []))] (fn [x] (instance? t x))))

(def byte-array? (is-array* byte-array))

(defn str-contains?
  [^String str ^String x] (not (neg? (.indexOf str x))))

(defn parse-int
  [^String x nf] (try (Integer/parseInt x) (catch Exception e)))

(defn parse-long
  [^String x nf] (try (Long/parseLong x) (catch Exception e)))

(defn parse-double
  [^String x nf] (try (Double/parseDouble x) (catch Exception e)))

(defn parse-boolean
  [^String x]
  (Boolean/parseBoolean x))

(defn get-filename
  [^String path]
  (subs path (inc (.lastIndexOf path "/"))))

(defn sha-256
  [input]
  (let [md (MessageDigest/getInstance "SHA-256")]
    (.update md (.getBytes input))
    (let [digest (.digest md)]
      (apply str (mapv #(format "%02x" (bit-and % 0xff)) digest)))))

(defn map->nsmap
  [m n]
  (reduce-kv (fn [acc k v]
               (let [new-kw
                     (if
                       (= "id" (name k))
                       (keyword "db" (name k))
                       (keyword (str n) (name k)))]
                 (assoc acc new-kw v)))
             {} m))

(defn fake-datomic
  "add a namespace to keys to look like a datom"
  [e]
  (let [kind (->kebab-case (:kind e))]
    (map->nsmap (dissoc e :kind) kind)))


(defn ?assoc
  "Same as assoc, but skip the assoc if v is nil"
  [m & kvs]
  (->> kvs
       (partition 2)
       (filter second)
       (map vec)
       (into m)))