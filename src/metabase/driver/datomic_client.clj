(ns metabase.driver.datomic-client
  (:require [datomic.client.api :as dc]
            [metabase.driver :as driver]
            [metabase.query-processor.store :as qp.store]
            [metabase.driver.datomic.query-processor :as qp]
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
   :nested-fields                          false
   :left-join                              false
   :right-join                             false
   :inner-join                             false
   :full-join                              false
   :set-timezone                           false
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

(defmethod driver/describe-database :datomic-client [_ instance]
  (let [db-spec (get instance :details)
        table-names (->> (latest-db db-spec)
                         (dc/qseq qp/schema-attrs-q)
                         (flatten)
                         (qp/derive-table-names))]
    {:tables
     (set
      (for [tname table-names]
        {:name   tname
         :schema nil}))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; driver/describe-table

(defn column-name [table-name col-kw]
  (if (= (namespace col-kw)
         table-name)
    (name col-kw)
    (util/kw->str col-kw)))

(defn describe-table [database {table-name :name}]
  (let [db          (latest-db (get database :details))
        schema      (dc/q qp/schema-attrs-q db)
        cols        (qp/table-columns table-name schema)]
    {:name   table-name
     :schema nil
     ;; Fields *must* be a set
     :fields
     (-> #{{:name          "db/id"
            :database-type "db.type/ref"
            :base-type     :type/PK
            :pk?           true}}
         (into (for [[col type] cols
                     :let [mb-type (qp/datomic->metabase-type type)]
                     :when mb-type]
                 {:name          (column-name table-name col)
                  :database-type (util/kw->str type)
                  :base-type     mb-type
                  :special-type  mb-type})))}))

(defmethod driver/describe-table :datomic-client [_ database table]
  (describe-table database table))

;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; driver/describe-table-fks

(defn guess-column-dest [db table-names col]
  (let [table? (into #{} table-names)
        attrs (-> {:find '[?ident]
                   :where [['_ col '?eid]
                           '[?eid ?attr]
                           '[?attr :db/ident ?ident]]}
                  (dc/q db)
                  (flatten))]
    (or (some->> attrs
                 (map namespace)
                 (remove #{"db"})
                 frequencies
                 (sort-by val)
                 last
                 key)
        (table? (name col)))))

(defn describe-table-fks [database {table-name :name}]
  (let [db     (latest-db (get database :details))
        schema (dc/q qp/schema-attrs-q db)
        tables (qp/derive-table-names schema)
        cols   (qp/table-columns table-name schema)]
    (-> #{}
        (into (for [[col type] cols
                    :when      (= type :db.type/ref)
                    :let       [dest (guess-column-dest db tables col)]
                    :when      dest]
                {:fk-column-name   (column-name table-name col)
                 :dest-table       {:name   dest
                                    :schema nil}
                 :dest-column-name "db/id"})))))

(defmethod driver/describe-table-fks :datomic-client [_ database table]
  (describe-table-fks database table))

;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; driver/mbql->native

(defn db-facade [db]
  (reify qp/DbFacade
    (cardinality-many? [this attrid]
      (= :db.cardinality/many
         (get-in
          (dc/pull db [{:db/cardinality [:db/ident]}] attrid)
          [:db/cardinality :db/ident])))

    (attr-type [this attrid]
      (get-in
       (dc/pull db [{:db/valueType [:db/ident]}] attrid)
       [:db/valueType :db/ident]))

    (entid [this ident]
      (:e (dc/datoms db {:index      :avet
                         :components [:db/ident ident]})))

    (entity [this eid]
      (dc/pull db '[*] eid))))

(defmethod driver/mbql->native :datomic-client [_ {mbqry :query
                                                   settings :settings}]
  (qp/mbql->native mbqry settings (db-facade (latest-db))))

;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; driver/execute-query

(defn execute-query [{:keys [native query] :as native-query}]
  (let [db      (latest-db)
        dqry    (qp/read-query (:query native))
        results (dc/q (dissoc dqry :fields) db nil)
      ;; Hacking around this is as it's so common in Metabase's automatic
      ;; dashboards. Datomic never returns a count of zero, instead it just
      ;; returns an empty result.
        results (if (and (empty? results)
                         (empty? (:breakout query))
                         (#{[[:count]] [[:sum]]} (:aggregation query)))
                  [[0]]
                  results)]
    (if query
      (qp/result-map-mbql (db-facade db) results dqry query)
      (qp/result-map-native results dqry))))

(defmethod driver/execute-query :datomic-client [_ native-query]
  (execute-query native-query))

(comment
  (def raven-spec
    {:endpoint "localhost:8998"
     :access-key "k"
     :secret "s"
     :db-name "m13n"})

  (driver/can-connect? :datomic-client raven-spec)

  (->> raven-spec
       (latest-db)
       (dc/q qp/schema-attrs-q)
       (qp/derive-table-names))
  (->> raven-spec
       (latest-db)
       (dc/q qp/schema-attrs-q)
       (qp/table-columns "txn"))

  (driver/describe-database
   :datomic-client
   {:details raven-spec})

  (driver/describe-table
   :datomic-client
   {:details raven-spec}
   {:name "txn"}))
