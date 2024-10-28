def call(Map config = [:]) {
    switch(config.get('doType')) {
        case 'download':
            minioDownload bucket: 'jenkins-devops',
                credentialsId: 'ce1a8e22-a4cd-4cbd-9f64-619d45155a86',
                failOnNonExisting: true,
                file: "${config.namespace}/${config.fileNamePath}", 
                host: 'http://172.16.60.9:9000', 
                targetFolder: './'
        break
        case 'upload':
            minio bucket: 'jenkins-devops', 
                credentialsId: 'ce1a8e22-a4cd-4cbd-9f64-619d45155a86',
                excludes: '', host: 'http://172.16.60.9:9000',
                includes: "${config.fileNamePath}",
                targetFolder: "${config.namespace}/"
        break
    }
}