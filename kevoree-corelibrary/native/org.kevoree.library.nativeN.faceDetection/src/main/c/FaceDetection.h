#include "thirdparty/component.h"

#include <dlfcn.h>

void dummy_function() { }
static const char *get_runtime_path ()
{
    Dl_info info;
    if (0 == dladdr((void*)dummy_function, &info)) return "unknown";
    return info.dli_fname;
}
char path_ressource[4096];

const char * getRessource(const char*key)
{
   int length;
   length = strlen(rindex(get_runtime_path(), '/'));
   memset(path_ressource,0,sizeof(path_ressource));
   strncpy(path_ressource,get_runtime_path(),strlen(get_runtime_path()) - length);
   strcat(path_ressource,"/");
   strcat(path_ressource,key);
  return path_ressource;
}

void faceDetected(void *input) {
 process_output(0,input);
}
void dispatch(int port,int id_queue)
{
    kmessage *msg = NULL;
          msg = dequeue(id_queue);
          if(msg !=NULL)
          {
             switch(port)
             {                     }
                     }

}int main (int argc,char *argv[])
{
   	if(argc  > 1)
    {
	    key_t key =   atoi(argv[1]);
	   // int port=   atoi(argv[2]);

	     bootstrap(key,-1);
        ctx->start= &start;
        ctx->stop = &stop;
        ctx->update   = &update;
        ctx->dispatch = &dispatch;
	    ctx->start();
    pause();
     }
}