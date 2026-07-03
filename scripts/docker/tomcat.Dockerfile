FROM tomcat:9.0-jdk11-temurin-jammy
ENV TZ=America/Lima
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
COPY lib/*.jar /usr/local/tomcat/lib/
COPY conf/tomcat-users.xml /usr/local/tomcat/conf/tomcat-users.xml
COPY conf/context.xml /usr/local/tomcat/conf/context.xml
COPY conf/server.xml /usr/local/tomcat/conf/server.xml
RUN cp -r /usr/local/tomcat/webapps.dist/* /usr/local/tomcat/webapps/ \
 && sed -i '/RemoteAddrValve/d' /usr/local/tomcat/webapps/manager/META-INF/context.xml \
 && sed -i '/RemoteAddrValve/d' /usr/local/tomcat/webapps/host-manager/META-INF/context.xml \
 && sed -i 's#<max-file-size>52428800</max-file-size>#<max-file-size>209715200</max-file-size>#g' /usr/local/tomcat/webapps/manager/WEB-INF/web.xml \
 && sed -i 's#<max-request-size>52428800</max-request-size>#<max-request-size>209715200</max-request-size>#g' /usr/local/tomcat/webapps/manager/WEB-INF/web.xml
EXPOSE 8080
