@Library('global-jenkins-library@1.8.0') _
buildJavaProject(
        buildInfo: getBuildInfo(),
        integrationTestsEnvVars: [],
        shouldPublishJars: true,
        shouldPublishDockerImages: true,
        dockerfileDir: 'build/resources/main',
        dockerfileFilename: 'Dockerfile.untrusted',
        buildContext: '.',
        preDevelopVisibility: 'iex.ec',
        developVisibility: 'iex.ec',
        preProductionVisibility: 'docker.io',
        productionVisibility: 'docker.io')
