# skel-pdf

PDF Tools

## Generate PDF from Template

1. Fonts must be in template directory
2. Fonts must be in TTF format
3. Reference to fonts in template: 

``` 
@font-face {
    font-family: 'Inconsolata';
    font-style: normal;
    -fs-pdf-font-embed: embed;
    -fs-pdf-font-encoding: Identity-H;
    src: url(./fonts/Inconsolata.ttf);
}
```

__NOTE__: Requires write permission to template dir (to write output.html)

```
./run-pdf <template dir> [output file] 
```

By default generates __output.pdf__

### Templates Examples

1. Simple: [tempates/T0](tempates/T0)
2. Images, Fonts, Styles: [tempates/T5](tempates/T5)


## Credits

1. Flying Saucer: [https://github.com/flyingsaucerproject/flyingsaucer/](https://github.com/flyingsaucerproject/flyingsaucer/)
2. Thymeleaf: [https://www.thymeleaf.org/doc](https://www.thymeleaf.org/doc))
