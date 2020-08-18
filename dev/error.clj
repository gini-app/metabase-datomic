(ns error)

(comment
  (import org.apache.commons.validator.routines.UrlValidator)

  (defmacro varargs
    {:style/indent 1}
    [klass & [objects]]
    (vary-meta `(into-array ~klass ~objects)
               assoc :tag (format "[L%s;" (.getCanonicalName ^Class (ns-resolve *ns* klass)))))

  (.isValid (UrlValidator. (varargs String ["http" "https"]) UrlValidator/ALLOW_LOCAL_URLS)
            "http://localhost:3000"))

(comment
  {:status :failed,
   :class java.lang.IllegalArgumentException,
   :error "No method in multimethod 'field-lvar' for dispatch value: :count",

   :stacktrace
   ("clojure.lang.MultiFn.getFn(MultiFn.java:156)"
    "clojure.lang.MultiFn.invoke(MultiFn.java:229)"
    "--> driver.datomic.query_processor$eval92823$fn__92825.invoke(query_processor.clj:607)"
    "driver.datomic.query_processor$apply_aggregation.invokeStatic(query_processor.clj:883)"
    "driver.datomic.query_processor$apply_aggregation.invoke(query_processor.clj:882)"
    "driver.datomic.query_processor$apply_aggregations.invokeStatic(query_processor.clj:897)"
    "driver.datomic.query_processor$apply_aggregations.invoke(query_processor.clj:896)"
    "driver.datomic.query_processor$mbqry->dqry.invokeStatic(query_processor.clj:949)"
    "driver.datomic.query_processor$mbqry->dqry.invoke(query_processor.clj:941)"
    "driver.datomic.query_processor$mbql->native$fn__93089.invoke(query_processor.clj:958)"
    "driver.datomic.query_processor$mbql->native.invokeStatic(query_processor.clj:958)"
    "driver.datomic.query_processor$mbql->native.invoke(query_processor.clj:952)"
    "driver.datomic$eval93476$fn__93477.invoke(datomic.clj:158)"
    "query_processor.middleware.mbql_to_native$query__GT_native_form$fn__53220.invoke(mbql_to_native.clj:17)"
    "query_processor.middleware.mbql_to_native$query__GT_native_form.invokeStatic(mbql_to_native.clj:16)"
    "query_processor.middleware.mbql_to_native$query__GT_native_form.invoke(mbql_to_native.clj:11)"
    "query_processor.middleware.mbql_to_native$mbql__GT_native$fn__53227.invoke(mbql_to_native.clj:34)"
    "query_processor.middleware.annotate$result_rows_maps__GT_vectors$fn__55518.invoke(annotate.clj:539)"
    "query_processor.middleware.annotate$add_column_info$fn__55424.invoke(annotate.clj:483)"
    "query_processor.middleware.cumulative_aggregations$handle_cumulative_aggregations$fn__56525.invoke(cumulative_aggregations.clj:57)"
    "query_processor.middleware.resolve_joins$resolve_joins$fn__60978.invoke(resolve_joins.clj:184)"
    "query_processor.middleware.limit$limit$fn__57582.invoke(limit.clj:19)"
    "query_processor.middleware.results_metadata$record_and_return_metadata_BANG_$fn__64093.invoke(results_metadata.clj:86)"
    "query_processor.middleware.format_rows$format_rows$fn__57562.invoke(format_rows.clj:26)"
    "query_processor.middleware.add_dimension_projections$add_remapping$fn__53941.invoke(add_dimension_projections.clj:234)"
    "query_processor.middleware.add_source_metadata$add_source_metadata_for_source_queries$fn__54595.invoke(add_source_metadata.clj:101)"
    "query_processor.middleware.resolve_source_table$resolve_source_tables$fn__61030.invoke(resolve_source_table.clj:48)"
    "query_processor.middleware.add_row_count_and_status$add_row_count_and_status$fn__54444.invoke(add_row_count_and_status.clj:16)"
    "query_processor.middleware.driver_specific$process_query_in_context$fn__56758.invoke(driver_specific.clj:12)"
    "query_processor.middleware.resolve_driver$resolve_driver$fn__60626.invoke(resolve_driver.clj:23)"
    "query_processor.middleware.bind_effective_timezone$bind_effective_timezone$fn__55869$fn__55870.invoke(bind_effective_timezone.clj:9)"
    "util.date$call_with_effective_timezone.invokeStatic(date.clj:88)"
    "util.date$call_with_effective_timezone.invoke(date.clj:77)"
    "query_processor.middleware.bind_effective_timezone$bind_effective_timezone$fn__55869.invoke(bind_effective_timezone.clj:8)"
    "query_processor.middleware.store$initialize_store$fn__64132$fn__64133.invoke(store.clj:11)"
    "query_processor.store$do_with_store.invokeStatic(store.clj:46)"
    "query_processor.store$do_with_store.invoke(store.clj:40)"
    "query_processor.middleware.store$initialize_store$fn__64132.invoke(store.clj:10)"
    "query_processor.middleware.async$async__GT_sync$fn__53122.invoke(async.clj:23)"
    "query_processor.middleware.async_wait$runnable$fn__55583.invoke(async_wait.clj:89)"),

   :query
   {:type "query",
    :query {:source-table 13, :breakout [["datetime-field" ["field-id" 70] "quarter-of-year"]], :aggregation [["count"]]},
    :parameters [],
    :async? true,
    :middleware {:add-default-userland-constraints? true, :userland-query? true},
    :info
    {:executed-by 1,
     :context :ad-hoc,
     :card-id nil,
     :nested? false,
     :query-hash [107, -8, 45, -47, -25, 17, -82, 85, -23, -50, 107, 27, -71, -87, -110, 33, -61, 13, 101, -23, 106, 92, 18, -25, -124, 54, 67, -81, 72, -87, -5, 71]},
    :constraints {:max-results 10000, :max-results-bare-rows 2000}},

   :preprocessed
   {:type :query,
    :database 3,
    :query
    {:source-table 13,
     :breakout [[:datetime-field [:field-id 70] :quarter-of-year]],
     :aggregation [[:aggregation-options [:count] {:name "count"}]],
     :order-by [[:asc [:datetime-field [:field-id 70] :quarter-of-year]]]},
    :middleware {:add-default-userland-constraints? true, :userland-query? true},
    :info
    {:executed-by 1,
     :context :ad-hoc,
     :nested? false,
     :query-hash [107, -8, 45, -47, -25, 17, -82, 85, -23, -50, 107, 27, -71, -87, -110, 33, -61, 13, 101, -23, 106, 92, 18, -25, -124, 54, 67, -81, 72, -87, -5, 71]},
    :constraints {:max-results 10000, :max-results-bare-rows 2000},
    :driver :datomic},
   :native nil})
