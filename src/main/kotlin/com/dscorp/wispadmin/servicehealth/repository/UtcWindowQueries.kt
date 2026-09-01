package com.dscorp.wispadmin.servicehealth.repository

import com.dscorp.wispadmin.servicehealth.domain.UtcInstantText
import com.dscorp.wispadmin.servicehealth.domain.HealthEvent
import com.dscorp.wispadmin.servicehealth.domain.OnuStateEvent
import com.dscorp.wispadmin.servicehealth.domain.OpticalSample
import com.dscorp.wispadmin.servicehealth.domain.RemoteAction
import com.dscorp.wispadmin.servicehealth.domain.WifiCountSample
import com.dscorp.wispadmin.servicehealth.domain.WifiStationHourly
import com.dscorp.wispadmin.servicehealth.domain.WifiStationSample
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.Instant

fun OpticalSampleRepository.listBySubscriptionInUtcWindow(id: Int, from: Instant, to: Instant): List<OpticalSample> =
    findOpticalBySubscriptionUtcRange(id, UtcInstantText.format(from), UtcInstantText.format(to))

fun OpticalSampleRepository.listByOnuInUtcWindow(id: Long, from: Instant, to: Instant): List<OpticalSample> =
    findOpticalByOnuUtcRange(id, UtcInstantText.format(from), UtcInstantText.format(to))

fun OnuStateEventRepository.listBySubscriptionInUtcWindow(id: Int, from: Instant, to: Instant): List<OnuStateEvent> =
    findStateBySubscriptionUtcRange(id, UtcInstantText.format(from), UtcInstantText.format(to))

fun WifiCountSampleRepository.listBySubscriptionInUtcWindow(id: Int, from: Instant, to: Instant): List<WifiCountSample> =
    findWifiCountBySubscriptionUtcRange(id, UtcInstantText.format(from), UtcInstantText.format(to))

fun WifiStationSampleRepository.listBySubscriptionInUtcWindow(id: Int, from: Instant, to: Instant): List<WifiStationSample> =
    findWifiStationBySubscriptionUtcRange(id, UtcInstantText.format(from), UtcInstantText.format(to))

fun WifiStationHourlyRepository.listBySubscriptionInUtcWindow(id: Int, from: Instant, to: Instant): List<WifiStationHourly> =
    findHourlyBySubscriptionUtcRange(id, UtcInstantText.format(from), UtcInstantText.format(to))

fun HealthEventRepository.pageBySubscriptionInUtcWindow(id: Int, from: Instant, to: Instant, page: Pageable): Page<HealthEvent> =
    findHealthEventBySubscriptionUtcRange(id, UtcInstantText.format(from), UtcInstantText.format(to), page)

fun RemoteActionRepository.listBySubscriptionCreatedInUtcWindow(id: Int, from: Instant, to: Instant): List<RemoteAction> =
    findRemoteActionBySubscriptionUtcRange(id, UtcInstantText.format(from), UtcInstantText.format(to))
