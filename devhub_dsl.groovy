#!/usr/bin/groovy
@Grab('org.yaml:snakeyaml:1.25')
import org.yaml.snakeyaml.Yaml

def workDir = SEED_JOB.getWorkspace()
def config = new Yaml().load(("${workDir}/devhub.yml" as File).text)

// Inject the environment variables on the step if present
def loadEnvironment(context, yamlVariables=[]) {
      String cmd = ""
      for(variables in yamlVariables) {
        for(variable in variables) {
          cmd += 'echo ' + variable.key + '=' + variable.value + " >> env.properties\n"
        }
      }

      if(cmd.trim()) {
        context.shell("rm -f env.properties\ntouch env.properties\n" + cmd)
      	context.environmentVariables {
          propertiesFile('env.properties')
        }
      }
}

job(config.name) {

  // The agent label to use
  label(config.build.techStack)

  // AWS credentials
  wrappers {
  credentialsBinding {
    amazonWebServicesCredentialsBinding {
      accessKeyVariable("AWS_ACCESS_KEY_ID")
      secretKeyVariable("AWS_SECRET_ACCESS_KEY")
      credentialsId("aws-dev")
    }
  }
  }

  // Git repo to checkout
  scm {
    git {
      remote {
        url(config.repo.repoUrl)
        branch(config.repo.branch)
        credentials(config.repo.githubTokenSecretId)
      }
    }
  }

  // This are the steps to execute
  steps {
    // Build
    if (config.build.buildCommand?.trim())
	  steps {
        // Load the environment variables for this step
        loadEnvironment(delegate, config.build.environment)

        // Execute the prebuild command
        if (config.build.preBuild?.trim()) {
          shell(config.build.preBuild)
        }

        shell(config.build.buildCommand)
      }

    // Unit Test
	if (config.unitTest.testCommand?.trim())
	  steps {
        // Load the environment variables for this step
        loadEnvironment(delegate, config.unitTest.environment)

        // Execute the prebuild command
        if (config.unitTest.preTest?.trim()) {
          shell(config.unitTest.preTest)
        }

        shell(config.unitTest.testCommand)
      }

    // Package
    if (config.package.packageCommand?.trim())
      steps {
        // Load the environment variables for this step
        loadEnvironment(delegate, config.package.environment)

        // Execute the prebuild command
        shell(config.package.packageCommand)

      }

    if (config.deploy.type?.trim())
      steps {
        // Load the environment variables for this step
        loadEnvironment(delegate, config.deploy.environment)
        shell(config.deploy.preDeploy)

		if (config.deploy.docker.image?.trim()) {
          dockerBuildAndPublish() {
            dockerfileDirectory(config.deploy.docker.dockerfile)
            repositoryName(config.deploy.docker.image)
            tag(config.deploy.docker.tag)
            registryCredentials(config.deploy.docker.credentials)
            dockerRegistryURL("https://" + config.deploy.docker.registry + "/")
            forcePull(false)
            forceTag(false)
          }
        }

        if (config.deploy.type == 'ECS') {
          shell("aws ecs update-service " +
                	" --service " + config.deploy.serviceName +
                	" --cluster " + config.deploy.cluster +
                	" --region us-east-1 --force-new-deployment")
        }
      }
  }

  publishers {
    archiveArtifacts {
      pattern(config.package.artifactDir + '/**/*')
      onlyIfSuccessful()
    }
  }
}
