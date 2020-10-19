$version: "1.0"

namespace com.amazon.alexasignalcomputationcontrolservice.filter

@documentation("Configuration for signal computation filter")
structure FilterConfiguration {
    filterConfiguration: Filter,
}

@documentation("A filter for signal computation. Options include JSON predicate and Safe Dynamic Config")
union Filter {
    jsonPredicateFilter: JsonPredicateFilter,
    safeDynamicConfigFilter: SafeDynamicConfigFilter,
}

@documentation("""
A filter based on JSON predicate. Effectively a \"WHERE\" clause.
For example, looking at this JSON payload:
{
  \"a\": {
    \"b\": \"This is a test\"
  }
}
This JSON predicate filter would evaluate to \"true\":
{
  \"op\": \"contains\",
  \"path\": \"/a/b\",
  \"value\": \" is a \"
}
""")
structure JsonPredicateFilter {
    @documentation("A single JSON path expression which is linked with binary comparison or boolean operators")
    expression: String,
}

@documentation("A Safe Dynamic Config (SDC) filter")
structure SafeDynamicConfigFilter {
    @required
    configName: String,
    @required
    odinMaterialSetName: String,
}