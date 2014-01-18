set Args = wscript.Arguments
if Args.Count < 2 then
   wscript.stderr.writeline( "? Invalid number of arguments")
   wscript.quit 1
end if

' Check for flags.
VrdAllowMultiple = false
lockFile = ""
profileName = "MPEG2 Program Stream"
profileNum = 1
c = "mpeg"
v = "mpeg2video"
for i = 1 to args.Count
   p = args(i-1)
   if left(p,3)="/l:" then lockFile = mid(p,4)
   if left(p,3)="/c:" then c = mid(p,4)
   if left(p,3)="/v:" then v = mid(p,4)
   if p = "/m" then VrdAllowMultiple = true
next

' Check that a lock file name was given
if ( lockFile = "" ) then
   wscript.stderr.writeline( "? Lock file (/l:) not given" )
   wscript.quit 2
end if

'  Decide on output types
if ( c = "mpegts" ) then
   profileName = "MPEG2 Transport Stream"
   profileNum = 4
   if ( v = "h264" ) then
      profileName = "H.264 Transport Stream"
   end if
end if
if ( c = "mp4" ) then
   profileName = "H.264 MP4"
end if

Set fso = CreateObject("Scripting.FileSystemObject")
sourceFile = args(0)
destFile   = args(1)

'Create VideoReDo object and open the source project / file.
if (VrdAllowMultiple) then
   Set VideoReDo = wscript.CreateObject( "VideoReDo.Application" )
   VideoReDo.SetQuietMode(true)
else
   Set VideoReDoSilent = wscript.CreateObject( "VideoReDo.VideoReDoSilent" )
   set VideoReDo = VideoReDoSilent.VRDInterface
end if

'Hard code no audio alert
VideoReDo.AudioAlert = false

' Open source file
openFlag = VideoReDo.FileOpen( sourceFile )

if openFlag = false then
   wscript.stderr.writeline( "? Unable to open file/project: " + sourceFile )
   wscript.quit 3
end if

' Open output file and start processing.
'NOTE: NEWER VRD TVSUITE4 NO LONGER SUPPORTS FileSaveAsEx so have to use FileSaveProfile
version = GetVersion(VideoReDo.VersionNumber)
if version < 4205604 then
   outputFlag = VideoReDo.FileSaveAsEx( destFile, profileNum )
   outputXML = ""
else
   outputFlag = true
   outputXML = VideoReDo.FileSaveProfile( destFile, profileName )
   if ( left(outputXML,1) = "*" ) then
      outputFlag = false
   end if
end if

if outputFlag = false then
   wscript.stderr.writeline("? Problem opening output file: " + destFile )
   wscript.stderr.writeline(outputXML)
   wscript.quit 4
end if

' Wait until output done and output % complete to stdout
while( VideoRedo.IsOutputInProgress() )
   percent = "Progress: " & Int(VideoReDo.OutputPercentComplete) & "%"
   wscript.echo(percent)
   if not fso.FileExists(lockFile) then
		VideoReDo.AbortOutput()
		endtime = DateAdd("s", 15, Now)
		while( VideoReDo.IsOutputInProgress() And (Now < endtime) )
			wscript.sleep 500
		wend
		VideoReDo.Close()
      wscript.quit 5
   end if
   wscript.sleep 2000
wend

' Close VRD
VideoReDo.Close()

' Exit with status 0
wscript.echo( "   Output complete to: " + destFile )
wscript.quit 0

function GetVersion(string)
   version = 0
   Set objRE = New RegExp
   With objRE
      .Pattern = "^(\S+).+$"
      .IgnoreCase = True
      .Global = False
   End With
   Set objMatch = objRE.Execute( string )
   If objMatch.Count = 1 Then
      v = objMatch.Item(0).Submatches(0)
      version = Replace(v, ".", "")
   End If
   Set objRE = Nothing
   Set objMatch = Nothing
   GetVersion = version
end function