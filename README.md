# Apollo Tracing Uploader for Java

A [GraphQL Java instrumentation](https://www.graphql-java.com/documentation/v12/instrumentation/) for uploading tracing metrics to the [Apollo Graph Manager](https://www.apollographql.com/docs/graph-manager/).

## Adding It To Your Project

Add the following to your `build.gradle`:

```groovy
dependencies {
  compile 'com.braintreepayments:apollo-tracing-uploader:0.4.0'
}
```

See the [end-to-end test](src/test/java/integration/EndToEndTest.java) for usage examples.
