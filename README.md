# RADAR-Gateway

[![Build Status](https://github.com/RADAR-base/RADAR-PushEndpoint/workflows/CI/badge.svg)](https://github.com/RADAR-base/RADAR-PushEndpoint/actions?query=workflow%3ACI+branch%3Adev+)
[![Docker Build](https://img.shields.io/docker/cloud/build/radarbase/radar-push-endpoint)](https://hub.docker.com/repository/docker/radarbase/radar-push-endpoint)

RADAR Push Endpoint that exposes REST interface for push subscription based APIs to the Apache
 Kafka.

## Configuration

Currently, Garmin is integrated. For adding more services, see the [Extending section](#extending).

```yaml
   pushIntegration:
      # Push service specific config
      garmin: 
        enabled: true
        userRepositoryClass: "org.radarbase.push.integration.garmin.user.ServiceUserRepository"
      service-n:
        enabled: true
        property-xyz: "value"
```

For Garmin, you will need to configure the endpoints in the [Garmin Developer Portal](https://healthapi.garmin.com/tools/updateEndpoints)

## Usage

Start the Service with

```shell
docker-compose up -d --build
```

then once `kafka-1` is ready, create topics with

```shell
TOPIC=test
docker-compose exec kafka-1 kafka-topics --create --topic $TOPIC --bootstrap-server kafka-1:9092
```

Now the service is accessible through <http://localhost:8090/push/integrations/>.
Garmin endpoints are available at -
- <http://localhost:8090/push/integrations/garmin/dailies>
- <http://localhost:8090/push/integrations/garmin/activities>
- <http://localhost:8090/push/integrations/garmin/activityDetails>
- <http://localhost:8090/push/integrations/garmin/manualActivities>
- <http://localhost:8090/push/integrations/garmin/epochs>
- <http://localhost:8090/push/integrations/garmin/sleeps>
- <http://localhost:8090/push/integrations/garmin/bodyCompositions>
- <http://localhost:8090/push/integrations/garmin/stress>
- <http://localhost:8090/push/integrations/garmin/userMetrics>
- <http://localhost:8090/push/integrations/garmin/moveIQ>
- <http://localhost:8090/push/integrations/garmin/pulseOx>
- <http://localhost:8090/push/integrations/garmin/respiration>
- <http://localhost:8090/push/integrations/garmin/deregister>

## Extending
This section walks through add a new push service integration. These should be implemented in a
 new package `org.radarbase.push.integration.<service-name>`.

### Resource
Create a new Resource and configure the endpoints required by the push service integration. For
 reference take a look at [GarminPushEndpoint](src/main/kotlin/org/radarbase/push/integration/garmin/resource/GarminPushEndpoint.kt)

### User Repository
Create a new UserRepository to provide user specific info and authorization info. This should
 implement the interface [UserRepository](src/main/kotlin/org/radarbase/push/integration/common/user/UserRepository.kt).
For reference, take a look at [ServiceUserRepository](src/main/kotlin/org/radarbase/push/integration/garmin/user/ServiceUserRepository.kt)

### Auth Validator
Create a new AuthValidator to check the requests and authorise with users provided by
 the User Repository. This can be done by implementing the [AuthValidator](https://github.com/RADAR-base/radar-jersey/blob/master/src/main/kotlin/org/radarbase/jersey/auth/AuthValidator.kt)
  interface provided by `radar-jersey` library.
For reference, take a look at [GarminAuthValidator](src/main/kotlin/org/radarbase/push/integration/garmin/auth/GarminAuthValidator.kt)

### Converter
This is optional but will help keep the code consistent. 
Create Converters for converting data posted by the push service to Kafka records. This can be
 done by implementing the [AvroConverter](src/main/kotlin/org/radarbase/push/integration/common/converter/AvroConverter.kt) interface.
For reference, take a look at converter implementations in [garmin converter](src/main/kotlin/org/radarbase/push/integration/garmin/converter) package.

### Configuration

Firstly, create a Resource Enhancer to register all your required classes to Jersey Context.
 Remember to use `named` to distinguish your service implementation.

```kotlin
class ServiceXIntegrationResourceEnhancer(private val config: Config) :
    JerseyResourceEnhancer {

    override fun ResourceConfig.enhance() {
        packages(
            "org.radarbase.push.integration.servicex.resource",
            "org.radarbase.push.integration.servicex.filter"
        )
    }

    override fun AbstractBinder.enhance() {

        bind(config.pushIntegration.servicex.userRepository)
            .to(UserRepository::class.java)
            .named("servicex")
            .`in`(Singleton::class.java)

        bind(ServiceXAuthValidator::class.java)
            .to(AuthValidator::class.java)
            .named("servicex")
            .`in`(Singleton::class.java)
    }
}
```

Next, add your `AuthValidator` to the [DelegatedAuthValidator](src/main/kotlin/org/radarbase/push/integration/common/auth/DelegatedAuthValidator.kt) so service specific Auth can be performed.
Make sure the path to your service's resources contain the matching string (`servicex` in this
 case).
 
```kotlin
...

    fun delegate(): AuthValidator {
        return when {
            uriInfo.matches(GARMIN_QUALIFIER) -> namedValidators.named(GARMIN_QUALIFIER).get()
            uriInfo.matches("servicex") -> namedValidators.named("servicex").get()
            // Add support for more as integrations are added
            else -> throw IllegalStateException()
        }
    }

...
```

Next, add the configuration to the [Config](src/main/kotlin/org/radarbase/gateway/Config.kt) class.
```kotlin
...
data class PushIntegrationConfig(
    val garmin: GarminConfig = GarminConfig(),
    val servicex: ServiceXConfig
)

data class ServiceXConfig(
    val enabled: Boolean = false,
    val userRepositoryClass: String,
    val property1: String,
    val property2: List<String>
)

...
```

Finally, add your newly created Resource Enhancer to [PushIntegrationEnhancerFactory](src/main/kotlin/org/radarbase/gateway/inject/PushIntegrationEnhancerFactory.kt)
```kotlin
...
        // Push Service specific enhancers
        if (config.pushIntegration.garmin.enabled) {
            enhancersList.add(GarminPushIntegrationResourceEnhancer(config))
        }
        if(config.pushIntegration.servicex.enabled) {
            enhancersList.add(ServiceXIntegrationResourceEnhancer(config))
        }


...
```

