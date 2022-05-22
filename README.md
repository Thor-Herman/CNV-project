# ImageProcessing

This project contains an application that offers a set of operations to transform images, as described in the [project description](http://grupos.ist.utl.pt/meic-cnv/project/CNV-21-22-Project.pdf).

Use `maven` to build the project:
  - `mvn package`

After building, a `jar` with all dependencies will be available at: `target/imageproc-1.0-SNAPSHOT-jar-with-dependencies.jar`.

Use `java` to run ImageProcessing locally:
  - `java -cp target/imageproc-1.0-SNAPSHOT-jar-with-dependencies.jar <transformation> <input image> <output image>`

Note that `<transformation>` should be replaced by the `class name` of the corresponding transformation that you want to test. Also, a number of sample images are included in the `res` directory.

ImageProcessing can also be ran as a webserver (see `WebServer.java`). Use `java` to start the server:
  - `java -cp target/imageproc-1.0-SNAPSHOT-jar-with-dependencies.jar pt.ulisboa.tecnico.cnv.imageproc.WebServer`

After starting the server, a request can be triggered using the following commands:

```
body=$(base64 --wrap=0 <input image>)
body=$(echo "data:image/<image format/extension>;base64,$body")
curl -s -d $body <IP/DNS name>:<port>/<transformation> -o /tmp/output.tmp
cat /tmp/output.tmp | base64 -d > <output image>
```

Note that the image needs to be sent and received as `base64` to and from the server.

Once you deployed your ImageProcessing in AWS, you can also use the a [web interface](http://grupos.ist.utl.pt/meic-cnv/project) to test your deployment.

Finally, you will note that the project is already ready to work with Lambda (i.e., Function-as-a-Service). Additional information will be provided when the topic is presented during the lab classes.
