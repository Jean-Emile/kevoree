#include "httpd.h"
#include "webserver.h"


int enable = 0;
pthread_t t_httpd;

char buf[1024];

struct sockaddr_in clientaddr;
socklen_t clientlen = sizeof clientaddr;

#define DEBUG


int default_port = 8080, listenfd, connfd;

void *   t_http_sever(void *p)
{

    listenfd = open_listenfd(default_port);
    if (listenfd > 0) {
        printf("listen on port %d, fd is %d\n", default_port, listenfd);
    } else {
        perror("ERROR");
        exit(listenfd);
    }
    // Ignore SIGPIPE signal, so if browser cancels the request, it
    // won't kill the whole process.
    signal(SIGPIPE, SIG_IGN);

    for(int i = 0; i < 10; i++) {
        int pid = fork();
        if (pid == 0) {         //  child
            while(enable == 1){
                connfd = accept(listenfd, (SA *)&clientaddr, &clientlen);
                process(connfd, &clientaddr);
                close(connfd);
            }
        } else if (pid > 0) {   //  parent
            printf("child pid is %d\n", pid);
        } else {
            perror("fork");
        }
    }

    while(enable == 1){
        connfd = accept(listenfd, (SA *)&clientaddr, &clientlen);
        process(connfd, &clientaddr);
        close(connfd);
    }

 }
/*@Start*/
int start()
{

 if(getDictionary("Port") != NULL){
        default_port = atoi(getDictionary("Port"));
    }

    strcpy(buf,"/tmp");

    if(getDictionary("ServerRoot") != NULL)
    {
        strcpy(buf,getDictionary("ServerRoot"));
    }

        fprintf(stderr,"Starting HTTP %d %s \n",default_port,buf);

    enable = 1;
    if (pthread_create (&t_httpd, NULL, &t_http_sever, NULL) != 0)
    {
       return -1;
    }
return 0;
}

/*@Stop */
int stop()
{
    fprintf(stderr,"Component stoping \n");
    enable = 0;
    pthread_kill(t_httpd,SIGKILL);

return 0;
}

/*@Update */
int update()
{
    fprintf(stderr,"Component updating \n");
        enable = 0;
    pthread_kill(t_httpd,9);
    sleep(1);
        if (pthread_create (&t_httpd, NULL, &t_http_sever, NULL) != 0)
        {
           return -1;
        }

 return 0;
}
