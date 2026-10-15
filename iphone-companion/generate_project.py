#!/usr/bin/python3
import plistlib,pathlib
r=pathlib.Path(__file__).resolve().parent
info={'CFBundleDisplayName':'Tripper Pair','CFBundleExecutable':'$(EXECUTABLE_NAME)','CFBundleIdentifier':'$(PRODUCT_BUNDLE_IDENTIFIER)','CFBundleName':'TripperPair','CFBundlePackageType':'APPL','CFBundleShortVersionString':'0.1','CFBundleVersion':'1','LSRequiresIPhoneOS':True,'NSBluetoothAlwaysUsageDescription':'Pair your iPhone with your Android Tripper bridge to share call notifications and music controls.','UILaunchScreen':{},'UISupportedInterfaceOrientations':['UIInterfaceOrientationPortrait']}
(r/'Info.plist').write_bytes(plistlib.dumps(info))
objects={}
def obj(n,isa,**kw):
 key='%024X'%n;objects[key]={'isa':isa,**kw};return key
swift=obj(1,'PBXFileReference',lastKnownFileType='sourcecode.swift',path='TripperPair.swift',sourceTree='<group>')
plist=obj(2,'PBXFileReference',lastKnownFileType='text.plist.xml',path='Info.plist',sourceTree='<group>')
app=obj(3,'PBXFileReference',explicitFileType='wrapper.application',path='TripperPair.app',sourceTree='BUILT_PRODUCTS_DIR',includeInIndex=0)
build=obj(4,'PBXBuildFile',fileRef=swift)
sources=obj(5,'PBXSourcesBuildPhase',buildActionMask=2147483647,files=[build],runOnlyForDeploymentPostprocessing=0)
frameworks=obj(6,'PBXFrameworksBuildPhase',buildActionMask=2147483647,files=[],runOnlyForDeploymentPostprocessing=0)
products=obj(7,'PBXGroup',children=[app],name='Products',sourceTree='<group>')
main=obj(8,'PBXGroup',children=[swift,plist,products],sourceTree='<group>')
settings={'PRODUCT_BUNDLE_IDENTIFIER':'local.re.tripperpair','PRODUCT_NAME':'TripperPair','INFOPLIST_FILE':'Info.plist','SWIFT_VERSION':'5.0','IPHONEOS_DEPLOYMENT_TARGET':'17.0','SDKROOT':'iphoneos','TARGETED_DEVICE_FAMILY':'1','CODE_SIGN_STYLE':'Automatic','ENABLE_USER_SCRIPT_SANDBOXING':'YES'}
configs=[obj(n,'XCBuildConfiguration',name=name,buildSettings=dict(settings,SWIFT_OPTIMIZATION_LEVEL='-Onone' if name=='Debug' else '-O')) for n,name in [(9,'Debug'),(10,'Release')]]
cl=obj(11,'XCConfigurationList',buildConfigurations=configs,defaultConfigurationIsVisible=0,defaultConfigurationName='Release')
target=obj(12,'PBXNativeTarget',name='TripperPair',productName='TripperPair',productType='com.apple.product-type.application',productReference=app,buildConfigurationList=cl,buildPhases=[sources,frameworks],buildRules=[],dependencies=[])
pc=[obj(n,'XCBuildConfiguration',name=name,buildSettings={'CLANG_ENABLE_MODULES':'YES','SDKROOT':'iphoneos','IPHONEOS_DEPLOYMENT_TARGET':'17.0'}) for n,name in [(13,'Debug'),(14,'Release')]]
pcl=obj(15,'XCConfigurationList',buildConfigurations=pc,defaultConfigurationIsVisible=0,defaultConfigurationName='Release')
project=obj(16,'PBXProject',attributes={'LastUpgradeCheck':'2600'},buildConfigurationList=pcl,compatibilityVersion='Xcode 14.0',developmentRegion='en',knownRegions=['en','Base'],mainGroup=main,productRefGroup=products,projectDirPath='',projectRoot='',targets=[target])
(r/'TripperPair.xcodeproj/project.pbxproj').write_bytes(plistlib.dumps({'archiveVersion':'1','classes':{},'objectVersion':'56','objects':objects,'rootObject':project}))
