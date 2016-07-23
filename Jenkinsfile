node ('debian-android') {
  stage 'Checkout'
  checkout scm

  stage 'Build'
  sh './gradlew build'

  stage 'Test'
  sh './gradlew connectedCheck'

  stage 'Assemble Release'
  sh './gradlew assembleRelease'
}
