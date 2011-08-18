#!/usr/bin/env python
import socket
import shlex
import subprocess
import os, os.path
import tempfile
import time
import threading

# Goal of this module:
# Get udp data from openmokast, convert to wav, convert to mp3, give to ezstream

localhost = "127.0.0.1"
UDP_BUFSIZE = 4096
SOCK_TIMEOUT = 2

ezstream_xml_template_fname = "ezstream.xml.template"

args_mpg123 = shlex.split("mpg123 -w - -")
args_lame = shlex.split("lame - -")
args_ezstream = shlex.split("ezstream -c") # ATTN! Followed by ezstream.xml !

class Colour:
    PURPLE = '\033[95m'
    BLUE = '\033[94m'
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    RED = '\033[91m'
    ENDC = '\033[0m'

def log(message):
    print(Colour.BLUE + "Openmokast->Icecast: {0}".format(message) + Colour.ENDC)

def warn(message):
    print(Colour.YELLOW + "Openmokast->Icecast: {0}".format(message) + Colour.ENDC)

def err(message):
    print(Colour.RED + "Openmokast->Icecast: {0}".format(message) + Colour.ENDC)

class OpenMokastIceCastAdapter(threading.Thread):
    """This class implements the following pipeline:
    nc -ul 127.0.0.1 40003 | mpg123 -w - -  | lame - - | ezstream -c ezstream.xml

    used to stream openmokast to icecast.
    """

    def __init__(self, udp_port, mount_point):
        threading.Thread.__init__(self)

        log("init")
        self.udp_port = udp_port
        self.mount_point = mount_point

        # filename of ezstream.xml configuration
        self.ezstream_xml_fname = None

        self.mpg123, self.lame, self.ezstream = [None] * 3

        self.ready = True
        self.running = False

    def __str__(self):
        return "<OM-Ice Adapter: {0} -> {1} [{2}]>".format(self.udp_port, self.mount_point, "ready" if self.ready else "stopped")

    def __del__(self):
        self.destroy()

    def destroy(self):
        log("destroying {0}".format(self))

        self.running = False
        self.ready = False

        try:
            self.sock.close()
        except:
            err("Failed to close socket")

        # kill subprocesses
        for i, p in enumerate([self.mpg123, self.lame, self.ezstream]):
            if p is not None:
                try:
                    log("terminating {0} {1}".format(i, p))
                    p.terminate()
                except:
                    err("Failed to term subprocesses")

        time.sleep(0.5)

        for i, p in enumerate([self.mpg123, self.lame, self.ezstream]):
            if p is not None:
                try:
                    rval = p.poll()
                    if rval is None: # process has not terminated
                        log("killing {0}".format(i))
                        p.kill()
                except:
                    err("Failed to kill subprocesses")

        # delete ezstream configuration file
        try:
            if self.ezstream_xml_fname is not None:
                log("erasing ezstream file")
                os.remove(self.ezstream_xml_fname)
        except OSError as e:
            if os.path.isfile(self.ezstream_xml_fname):
                print("WARNING: Could not remove temporary file {0}".format(self.ezstream_xml_fname))

        log("{0} destroy complete".format(self))

    def stop(self):
        log("Stopping {0}".format(self))
        self.running = False
        self.sock.close()

    def run(self):
        try:
            self.prepare_socket()
            self.prepare_mpg123()
            self.prepare_lame()
            self.prepare_ezstream()
            self.prepare_finalise()
        except Exception as e:
            err("preparation failed")
            import traceback, sys
            print(Colour.RED)
            traceback.print_exc(file=sys.stdout)
            print(Colour.ENDC)
            self.destroy()
            return

        self.running = True

        data = ""

        try:
            log("starting loop")
            while self.running:
                try:
                    data = self.sock.recv(UDP_BUFSIZE)
                except socket.timeout:
                    warn("No data received for {0} seconds".format(SOCK_TIMEOUT))
                    continue
                self.mpg123.communicate(data)
        finally:
            log("loop terminated")
            self.destroy()

    def prepare_socket(self):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock.settimeout(SOCK_TIMEOUT)
        self.sock.bind((localhost, self.udp_port))

    def prepare_mpg123(self):
        self.mpg123 = subprocess.Popen(args_mpg123, stdin=subprocess.PIPE, stdout=subprocess.PIPE)

    def prepare_lame(self):
        self.lame = subprocess.Popen(args_lame, stdin=self.mpg123.stdout, stdout=subprocess.PIPE)

    def prepare_ezstream(self):
        # First create the template
        ezstream_xml_fd, self.ezstream_xml_fname = tempfile.mkstemp(suffix='.ezstream.xml')

        ezstream_xml_file = os.fdopen(ezstream_xml_fd, "w")

        ezstream_xml_template = open(ezstream_xml_template_fname).read()

        ezstream_xml_file.write(ezstream_xml_template.format(mountpoint=self.mount_point))
        ezstream_xml_file.close()
        
        self.ezstream = subprocess.Popen(args_ezstream + [self.ezstream_xml_fname], stdin=self.lame.stdout, stdout=None)


    def prepare_finalise(self):
        # Close the stdouts so that the processes can get a SIGPIPE when the reading process quits
        self.mpg123.stdout.close()
        self.lame.stdout.close()

if __name__ == "__main__":
    def p(m):
        print(Colour.GREEN + "================" + m + Colour.ENDC)

    p("TEST")

    adapt = OpenMokastIceCastAdapter(2720, "testing")
    
    p("START")
    adapt.start()

    try:
        p("SLEEPING 60 SECS")
        time.sleep(60)

        p("STOPPING")
    except KeyboardInterrupt:
        p("STOPPING")
        adapt.stop()

    p("JOIN")
    adapt.destroy()
    adapt.join()
    
    p("DONE")
