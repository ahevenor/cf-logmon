package org.cloudfoundry.loggregator.logmon.cf

import org.cloudfoundry.client.CloudFoundryClient
import org.cloudfoundry.client.v2.organizations.GetOrganizationRequest
import org.cloudfoundry.client.v2.organizations.GetOrganizationResponse
import org.cloudfoundry.client.v2.spaces.GetSpaceRequest
import org.cloudfoundry.client.v2.spaces.GetSpaceResponse
import org.cloudfoundry.doppler.DopplerClient
import org.cloudfoundry.doppler.LogMessage
import org.cloudfoundry.operations.DefaultCloudFoundryOperations
import org.cloudfoundry.operations.applications.GetApplicationRequest
import org.cloudfoundry.operations.applications.LogsRequest
import org.cloudfoundry.uaa.UaaClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

@Component
open class LogStreamer @Autowired constructor(
    val cloudFoundryClient: CloudFoundryClient,
    val dopplerClient: DopplerClient,
    val uaaClient: UaaClient,
    val cfApplicationEnv: CfApplicationEnv
) {
    data class Application(val orgName: String, val spaceName: String, val appName: String)

    open fun logStreamForApplication(application: Application): Flux<LogMessage> {
        val logsReq = LogsRequest.builder().name(application.appName).build()

        return client(application.orgName, application.spaceName).applications().logs(logsReq)
    }

    open fun fetchApplicationByName(name: String): Application? {
        val space = fetchSpaceById(cfApplicationEnv.spaceId)
        val organization = fetchOrganizationById(space.entity.organizationId)

        return client(organization.entity.name, space.entity.name).applications()
            .get(GetApplicationRequest.builder().name(name).build())
            .map { Application(organization.entity.name, space.entity.name, it.name) }
            .block()
    }

    fun fetchSpaceById(spaceId: String): GetSpaceResponse =
        cloudFoundryClient.spaces()
            .get(GetSpaceRequest.builder().spaceId(spaceId).build())
            .block()

    private fun fetchOrganizationById(orgId: String): GetOrganizationResponse =
        cloudFoundryClient.organizations()
            .get(GetOrganizationRequest.builder().organizationId(orgId).build())
            .block()

    private var _client: DefaultCloudFoundryOperations? = null

    private fun client(orgId: String, spaceId: String): DefaultCloudFoundryOperations {
        if (_client == null) {
            _client = DefaultCloudFoundryOperations.builder()
                .cloudFoundryClient(cloudFoundryClient)
                .dopplerClient(dopplerClient)
                .uaaClient(uaaClient)
                .organization(orgId)
                .space(spaceId)
                .build()
        }
        return _client!!
    }
}
