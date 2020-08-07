(ns metabase.driver.datomic-client
  (:require [datomic.client.api :as dc]
            [metabase.driver :as driver]
            [metabase.query-processor.store :as qp.store]

            [metabase.driver.datomic.util :as util]))

(driver/register! :datomic-client)

(defn latest-db
  ([] (latest-db (get (qp.store/database) :details)))
  ([db-spec]
   ;; merge default with provided spec so client can override server-type
   (-> {:server-type :peer-server
        :validate-hostnames false}
       (merge db-spec)
       (dc/client)
       (dc/connect db-spec)
       (dc/db))))

(def features
  {:basic-aggregations                     true
   :standard-deviation-aggregations        true
   :case-sensitivity-string-filter-options true
   :foreign-keys                           true
   :nested-queries                         true
   :expressions                            false
   :expression-aggregations                false
   :native-parameters                      false
   :binning                                false})

(doseq [[feature supported?] features]
  (defmethod driver/supports? [:datomic-client feature] [_ _] supported?))

(defn can-connect? [db-spec]
  (try
    (latest-db db-spec)
    true
    (catch Exception e
      false)))

(defmethod driver/can-connect? :datomic-client [_ db-spec]
  (can-connect? db-spec))

;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; driver/describe-database

(def reserved-prefixes
  #{"fressian"
    "db"
    "db.alter"
    "db.excise"
    "db.install"
    "db.sys"
    "db.attr"
    "db.entity"})

(defn attr-entities
  "Query db for all attribute entities."
  [db]
  (flatten (dc/qseq '{:find [(pull ?e [*])] :where [[?e :db/valueType]]} db)))

(defn attrs-by-table
  "Map from table name to collection of attribute entities."
  [db]
  (reduce #(update %1 (namespace (:db/ident %2)) conj %2)
          {}
          (attr-entities db)))

(defn derive-table-names
  "Find all \"tables\" i.e. all namespace prefixes used in attribute names."
  [db]
  ;; TODO(alan) Use pattern match to remove all datomic reserved prefixes
  (remove reserved-prefixes
          (keys (attrs-by-table db))))

(defmethod driver/describe-database :datomic-client [_ instance]
  (let [db-spec (get instance :details)
        table-names (derive-table-names (latest-db db-spec))]
    {:tables
     (set
      (for [tname table-names]
        {:name   tname
         :schema nil}))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; driver/describe-table

(derive :type/Keyword :type/Text)

(def datomic->metabase-type
  {:db.type/keyword :type/Keyword
   :db.type/string  :type/Text
   :db.type/boolean :type/Boolean
   :db.type/long    :type/Integer
   :db.type/bigint  :type/BigInteger
   :db.type/float   :type/Float
   :db.type/double  :type/Float
   :db.type/bigdec  :type/Decimal
   :db.type/ref     :type/FK
   :db.type/instant :type/DateTime
   :db.type/uuid    :type/UUID
   :db.type/uri     :type/URL
   :db.type/bytes   :type/Array

   ;; TODO(alan) Unhandled types
   ;; :db.type/symbol
   ;; :db.type/tuple
   ;; :db.type/uri    causes error on FE (Reader can't process tag object)
   })

(defn table-columns
  "Given the name of a \"table\" (attribute namespace prefix), find all attribute
  names that occur in entities that have an attribute with this prefix."
  [db table]
  (->> table
       (get (attrs-by-table db))
       (map :db/ident)
       (dc/q
        '{:find [?attr ?type]
          :where [[?e-schema :db/ident ?attr]
                  [?e-schema :db/valueType ?e-type]
                  [?e-type :db/ident ?type]]
          :in [$ [?attr ...]]}
        db)))

(defn column-name [table-name col-kw]
  (if (= (namespace col-kw)
         table-name)
    (name col-kw)
    (util/kw->str col-kw)))

(defn describe-table [database {table-name :name}]
  (let [db          (latest-db (get database :details))
        cols        (table-columns db table-name)]
    {:name   table-name
     :schema nil
     ;; Fields *must* be a set
     :fields
     (-> #{{:name          "db/id"
            :database-type "db.type/ref"
            :base-type     :type/PK
            :pk?           true}}
         (into (for [[col type] cols
                     :let [mb-type (datomic->metabase-type type)]
                     :when mb-type]
                 {:name          (column-name table-name col)
                  :database-type (util/kw->str type)
                  :base-type     mb-type
                  :special-type  mb-type})))}))

(defmethod driver/describe-table :datomic-client [_ database table]
  (describe-table database table))

(def raven-spec
  {:endpoint "localhost:8998"
   :access-key "k"
   :secret "s"
   :db-name "m13n"})

(comment
  (driver/can-connect? :datomic-client raven-spec)

  (attr-entities (latest-db raven-spec))
  (attrs-by-table (latest-db raven-spec))
  (derive-table-names (latest-db raven-spec))
  (table-columns (latest-db raven-spec) "txn")

  (driver/describe-database
   :datomic-client
   {:details raven-spec})

  (driver/describe-table
   :datomic-client
   {:details raven-spec}
   {:name "txn"}))
