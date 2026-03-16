package com.caskfive.pingcheck.di

import com.caskfive.pingcheck.domain.ping.PingBinaryProvider
import com.caskfive.pingcheck.domain.ping.SystemPingBinaryProvider
import com.caskfive.pingcheck.domain.process.ProcessExecutor
import com.caskfive.pingcheck.domain.process.SystemProcessExecutor
import com.caskfive.pingcheck.service.PingServiceManager
import com.caskfive.pingcheck.service.PingServiceManagerImpl
import com.caskfive.pingcheck.util.DnsResolver
import com.caskfive.pingcheck.util.NetworkChecker
import com.caskfive.pingcheck.util.SystemDnsResolver
import com.caskfive.pingcheck.util.SystemNetworkChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideProcessExecutor(): ProcessExecutor = SystemProcessExecutor()

    @Provides
    @Singleton
    fun providePingBinaryProvider(): PingBinaryProvider = SystemPingBinaryProvider()

    @Provides
    @Singleton
    fun provideDnsResolver(impl: SystemDnsResolver): DnsResolver = impl

    @Provides
    @Singleton
    fun provideNetworkChecker(impl: SystemNetworkChecker): NetworkChecker = impl

    @Provides
    @Singleton
    fun providePingServiceManager(impl: PingServiceManagerImpl): PingServiceManager = impl
}
