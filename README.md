# RADAR-Gateway

[![Build Status](https://travis-ci.org/RADAR-base/RADAR-PushEndpoint.svg?branch=master)](https
://travis-ci.org/RADAR-base/RADAR-PushEndpoint)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/79b2380112c5451181367ae16e112025)](https://www.codacy.com/app/RADAR-base/RADAR-PushEndpoint?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=RADAR-base/RADAR-Gateway&amp;utm_campaign=Badge_Grade)
[![Docker Build](https://img.shields.io/docker/build/radarbase/radar-push-endpoint.svg)](https
://cloud.docker.com/swarm/radarbase/repository/docker/radarbase/radar-push-endpoint/general)

RADAR Push Endpoint that exposes REST interface for push subscription based APIs to the Apache
 Kafka.

## Configuration

Currently, Garmin is integrated. For adding more services, see the [Extending section](#extending).

```yaml
   pushIntegration:
      enabledServices: ["garmin", "service-2", ..., "service-n"]
      # Push service specific config
      garmin: 
        userRepositoryClass: "org.radarbase.push.integration.garmin.user.ServiceUserRepository"
      service-n:
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

Now the service is accessible through <http://localhost:8090/push/integration/>.
Garmin endpoints are available at -
- <http://localhost:8090/push/integration/garmin/dailies>
- <http://localhost:8090/push/integration/garmin/activities>
- <http://localhost:8090/push/integration/garmin/activityDetails>
- <http://localhost:8090/push/integration/garmin/manualActivities>
- <http://localhost:8090/push/integration/garmin/epochs>
- <http://localhost:8090/push/integration/garmin/sleeps>
- <http://localhost:8090/push/integration/garmin/bodyCompositions>
- <http://localhost:8090/push/integration/garmin/stress>
- <http://localhost:8090/push/integration/garmin/userMetrics>
- <http://localhost:8090/push/integration/garmin/moveIQ>
- <http://localhost:8090/push/integration/garmin/pulseOx>
- <http://localhost:8090/push/integration/garmin/respiration>
- <http://localhost:8090/push/integration/garmin/deregister>

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
    val enabledServices: List<String> = listOf("garmin", "servicex"),
    val garmin: GarminConfig = GarminConfig(),
    val servicex: ServiceXConfig
)

data class ServiceXConfig(
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
        config.pushIntegration.enabledServices.forEach { service ->
            enhancersList.addAll(
                when (service) {
                    "garmin" -> listOf(GarminPushIntegrationResourceEnhancer(config))
                    "servicex" -> listOf(ServiceXIntegrationResourceEnhancer(config))

                    // Add more enhancers as the integrations are added
                    else -> throw IllegalStateException(
                        "The configured push integration for $service is not " +
                                "available."
                    )
                }
            )
        }

...
```

